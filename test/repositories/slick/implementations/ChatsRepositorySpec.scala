package repositories.slick.implementations

import model.dtos.{ CreateChatDTO, CreateEmailDTO }
import model.types.Mailbox._
import model.types.{ Mailbox, ParticipantType }
import model.types.ParticipantType._
import org.scalatest._
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import repositories.dtos.ChatPreview
import repositories.slick.mappings._
import slick.jdbc.MySQLProfile.api._
import utils.TestGenerators._

import scala.concurrent.duration.Duration
import scala.concurrent._

class ChatsRepositorySpec extends AsyncWordSpec with OptionValues with MustMatchers with BeforeAndAfterAll
  with Inside with BeforeAndAfterEach {

  private lazy val appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
  private lazy val injector: Injector = appBuilder.injector()
  private val db = injector.instanceOf[Database]
  implicit val ec: ExecutionContext = injector.instanceOf[ExecutionContext]
  val chatsRep = new SlickChatsRepository(db)

  //region Befores and Afters

  override def beforeAll(): Unit = {
    Await.result(db.run(DBIO.seq(
      AddressesTable.all.schema.create,
      UsersTable.all.schema.create,
      ChatsTable.all.schema.create,
      EmailsTable.all.schema.create,
      EmailAddressesTable.all.schema.create,
      UserChatsTable.all.schema.create,
      OversightsTable.all.schema.create,
      AttachmentsTable.all.schema.create,
      PasswordsTable.all.schema.create,
      TokensTable.all.schema.create)), Duration.Inf)
  }

  override def beforeEach(): Unit = {
  }

  override def afterEach(): Unit = {
    Await.result(db.run(DBIO.seq(
      AddressesTable.all.delete,
      UsersTable.all.delete,
      ChatsTable.all.delete,
      EmailsTable.all.delete,
      EmailAddressesTable.all.delete,
      UserChatsTable.all.delete,
      OversightsTable.all.delete,
      AttachmentsTable.all.delete,
      PasswordsTable.all.delete,
      TokensTable.all.delete)), Duration.Inf)
  }

  override def afterAll(): Unit = {
    Await.result(db.run(DBIO.seq(
      AddressesTable.all.schema.drop,
      UsersTable.all.schema.drop,
      ChatsTable.all.schema.drop,
      EmailsTable.all.schema.drop,
      EmailAddressesTable.all.schema.drop,
      UserChatsTable.all.schema.drop,
      OversightsTable.all.schema.drop,
      AttachmentsTable.all.schema.drop)), Duration.Inf)
  }
  //endregion

  case class UserInfo(address: String, userId: String)

  //region Auxiliary Methods

  /**
   * Creates a DBIOAction that generates a User and registers them into the Database
   * @return Action that returns a UserInfo case class that contains a User's Address and UserId
   */
  def newUser: DBIO[UserInfo] = {
    val userId = genUUID.sample.value
    val userAddress = genEmailAddress.sample.value
    val firstName = genString.sample.value
    val lastName = genString.sample.value

    val insertUser = for {
      addressId <- chatsRep.insertAddressIfNotExists(userAddress)
      _ <- UsersTable.all += UserRow(userId, addressId, firstName, lastName)
    } yield addressId

    insertUser.map(_ => UserInfo(userAddress, userId))
  }

  def sendEmailTo(createEmailDTO: CreateEmailDTO, chatId: String,
    receiverUserChatId: String, sent: Boolean): DBIO[ChatPreview] = {

    for {
      senderInfo <- newUser
      _ <- giveUserChatAccess(senderInfo.userId, chatId)
      optChat <- chatsRep.postEmailAction(createEmailDTO, chatId, senderInfo.userId)

      chatpreview <- {
        val chat = optChat.value
        val email = chat.email
        (if (sent) sendDraft(email.emailId.value, receiverUserChatId, To)
        else DBIO.successful(()))
          .map(_ => ChatPreview(chat.chatId.value, chat.subject.value,
            senderInfo.address, email.date.value, email.body.value))
      }
    } yield chatpreview
  }

  def sendDraft(emailId: String, viewerUserChatId: String, participantType: ParticipantType): DBIO[Boolean] =
    for {
      emailSent <- EmailsTable.all.filter(_.emailId === emailId).map(_.sent).update(1)
      mailBoxUpdated <- participantType match {
        case From => UserChatsTable.all.filter(_.userChatId === viewerUserChatId)
          .map(userChatRow => (userChatRow.sent, userChatRow.draft))
          .update((1, 0))
        case _ => UserChatsTable.all.filter(_.userChatId === viewerUserChatId)
          .map(_.inbox)
          .update(1)
      }
    } yield mailBoxUpdated == emailSent && emailSent == 1

  def insertEmailTest4(participantType: Option[ParticipantType], chatId: String, viewerInfo: UserInfo,
    viewerUserChatId: String, sent: Boolean): DBIO[ChatPreview] = {
    val createEmailDTO = genCreateEmailDTOPost.sample.value

    participantType match {
      case Some(From) =>
        for {
          optChat <- chatsRep.postEmailAction(createEmailDTO.copy(from = viewerInfo.address), chatId, viewerInfo.userId)
          sentOptChat <- if (sent) sendDraft(optChat.value.email.emailId.value, viewerUserChatId, From)
            .map(_ => optChat)
          else DBIO.successful(optChat)

        } yield {
          val chat = sentOptChat.value
          val email = chat.email
          ChatPreview(chat.chatId.value, chat.subject.value, viewerInfo.address, email.date.value, email.body.value)
        }

      case Some(To) => sendEmailTo(
        createEmailDTO.copy(to = Some(createEmailDTO.to.getOrElse(Set.empty[String]) + viewerInfo.address)),
        chatId, viewerUserChatId, sent)

      case Some(Cc) => sendEmailTo(
        createEmailDTO.copy(cc = Some(createEmailDTO.to.getOrElse(Set.empty[String]) + viewerInfo.address)),
        chatId, viewerUserChatId, sent)

      case Some(Bcc) => sendEmailTo(
        createEmailDTO.copy(bcc = Some(createEmailDTO.to.getOrElse(Set.empty[String]) + viewerInfo.address)),
        chatId, viewerUserChatId, sent)

      case None =>
        for {
          receiverInfo <- newUser
          receiverUserChatId <- giveUserChatAccess(receiverInfo.userId, chatId)
          chatPreview <- sendEmailTo(
            createEmailDTO.copy(to = Some(createEmailDTO.to.getOrElse(Set.empty[String]) + receiverInfo.address)),
            chatId, receiverUserChatId, sent)
        } yield chatPreview
    }
  }

  def insertEmail(chatId: String, viewerInfo: UserInfo, viewerUserChatId:
  String): DBIO[Option[(ChatPreview, ParticipantType, Boolean)]] = {

    val createEmailDTO = genCreateEmailDTOPost.sample.value
    val participantType = genParticipantType.sample.value
    val sent = genBoolean.sample.value

    participantType match {
      case Some(From) =>
        for {
          optChat <- chatsRep.postEmailAction(createEmailDTO.copy(from = viewerInfo.address), chatId, viewerInfo.userId)
          _ <- if (sent) sendDraft(optChat.value.email.emailId.value, viewerUserChatId, From)
          else DBIO.successful(())

        } yield {
          val chat = optChat.value
          val email = chat.email
          Some((
            ChatPreview(chat.chatId.value, chat.subject.value, viewerInfo.address, email.date.value, email.body.value),
            From, sent))
        }

      case Some(To) => sendEmailTo(
        createEmailDTO.copy(to = Some(createEmailDTO.to.getOrElse(Set.empty[String]) + viewerInfo.address)),
        chatId, viewerUserChatId, sent).map(chatPreview => if (sent) Some((chatPreview, To, sent))
        else None)

      case Some(Cc) => sendEmailTo(
        createEmailDTO.copy(cc = Some(createEmailDTO.to.getOrElse(Set.empty[String]) + viewerInfo.address)),
        chatId, viewerUserChatId, sent).map(chatPreview => if (sent) Some((chatPreview, Cc, sent))
        else None)

      case Some(Bcc) => sendEmailTo(
        createEmailDTO.copy(bcc = Some(createEmailDTO.to.getOrElse(Set.empty[String]) + viewerInfo.address)),
        chatId, viewerUserChatId, sent).map(chatPreview => if (sent) Some((chatPreview, Bcc, sent))
        else None)

      case None =>
        for {
          receiverInfo <- newUser
          receiverUserChatId <- giveUserChatAccess(receiverInfo.userId, chatId)
          chatPreview <- sendEmailTo(
            createEmailDTO.copy(to = Some(createEmailDTO.to.getOrElse(Set.empty[String]) + receiverInfo.address)),
            chatId, receiverUserChatId, sent)
        } yield None
    }
  }

  /**
   * Builds a DBIOAction that checks if a given user has access to a given chat.
   * If not, gives the user access by creating a row on the UserChatTable with all the Mailboxes turned to 0
   * and returns the Id of said row.
   * Otherwise returns the Id of the already existing row.
   * @param userId The Id of the user
   * @param chatId The Id of the chat
   * @return A DBIOAction containing the Id of the UserChat Row
   */
  def giveUserChatAccess(userId: String, chatId: String): DBIO[String] =
    UserChatsTable.all.filter(userChatRow => userChatRow.userId === userId && userChatRow.chatId === chatId)
      .map(_.userChatId).result.headOption.flatMap {
        case Some(userChatId) => DBIO.successful(userChatId)
        case None =>
          val newUserChatId = genUUID.sample.value
          UserChatsTable.all.+=(UserChatRow(newUserChatId, userId, chatId, 0, 0, 0, 0))
            .andThen(DBIO.successful(newUserChatId))
      }

  def participantIsReceivingTest4(participantType: Option[ParticipantType]): Boolean =
    participantType.contains(To) ||
      participantType.contains(Cc) ||
      participantType.contains(Bcc)

  def participantIsReceiving(participantType: ParticipantType): Boolean =
    participantType == To ||
      participantType == Cc ||
      participantType == Bcc

  /**
   * Receives the Id of the viewer and creates a DBIOAction that creates a new Chat in the DB and
   * gives the viewer access to it by creating a UserCHat row.
   * Returns a CreateChatDTO and the Id of the viewer's UserChat row.
   * Note that another User is also created in order to make the opening email of the chat.
   * @param viewerId The Id of the viewer
   * @return A DBIOAction that returns a tuple of the chat's CreateChatDTO and the viewer's UserChat row
   */
  def newChat(viewerId: String): DBIO[(CreateChatDTO, String)] = {
    for {
      userInfo <- newUser
      createChatDTO = genCreateChatDTOPost.sample.value
      newChat <- chatsRep.postChatAction(createChatDTO, userInfo.userId)
      userChatId <- giveUserChatAccess(viewerId, newChat.chatId.value)
    } yield (newChat, userChatId)
  }

  def visibleToMailbox(participantType: ParticipantType, sent: Boolean, mailbox: Mailbox): Boolean =
    mailbox match {
      case Inbox => participantIsReceiving(participantType) && sent
      case Sent => participantType == From && sent
      case Drafts => participantType == From && !sent
      case Trash => participantIsReceiving(participantType) && sent || participantType == From
    }

  /* def fillChatNOTDEBUG(chatId: String, viewerInfo: UserInfo, viewerUserChatId: String, mailbox: Mailbox): Future[Option[ChatPreview]] =
    Future.sequence(
      genListOfT(_ => insertEmail(chatId, viewerInfo, viewerUserChatId)).sample.value)
      .map(_.filter { case (chatPreview, participantType, sent) => visibleToMailbox(participantType, sent, mailbox) }
        .map(_._1)
        .sortBy(chatpreview => (chatpreview.lastEmailDate, chatpreview.contentPreview, chatpreview.lastAddress))
        .headOption)*/

  def fillChat(chatId: String, viewerInfo: UserInfo, viewerUserChatId: String, mailbox: Mailbox): DBIO[Option[ChatPreview]] = {
    val emaiList = DBIO.sequence(
      genListOfT(_ => insertEmail(chatId, viewerInfo, viewerUserChatId)).sample.value)
      .map(_
        .flatten.sortBy {
          case (chatPreview, _, _) => (chatPreview.lastEmailDate, chatPreview.contentPreview, chatPreview.lastAddress)
        })

    val chatIsVisible: DBIO[Boolean] = emaiList.map(_.foldLeft(false) {
      case (agg, (chatPreview, participantType, sent)) => agg || visibleToMailbox(participantType, sent, mailbox)
    })

    chatIsVisible.flatMap(
      if (_) emaiList.map(_.headOption.map(_._1))
      else DBIO.successful(None))
  }

  

  /*  def makeChatsNOTDEBUG(viewerInfo: UserInfo, mailbox: Mailbox): Future[List[ChatPreview]] =
    Future.sequence(
      genListOfT(_ => newChat(viewerInfo.userId)).sample.value
        .map(futureOfCreateChat => for {
          (createChatDTO, viewerUserChatId) <- futureOfCreateChat
          optChatPreview <- fillChat(createChatDTO.chatId.value, viewerInfo, viewerUserChatId, mailbox)
          optDeletedChatPreview <- optChatPreview match {
            case Some(chatPreview) =>
              val delete = genBoolean.sample.get

              if (delete) chatsRep.moveChatToTrash(chatPreview.chatId, viewerInfo.userId).map(_ =>
                mailbox match {
                  case Trash => Some(chatPreview)
                  case _ => None
                })
              else Future.successful(mailbox match {
                case Trash => None
                case _ => Some(chatPreview)
              })

            case None => Future.successful(None)
          }

        } yield optDeletedChatPreview)).map(_.flatten)*/

  /*  def makeChats(viewerInfo: UserInfo, mailbox: Mailbox): Future[List[ChatPreview]] =
    Future.sequence(
      {
        val a = genListOfT(_ => newChat(viewerInfo.userId)).sample.value
        println(
          "THIS IS THE GENERATED LIST OF CHATS",
          Await.result(Future.sequence(a), Duration.Inf))
        a
      }
        .map(futureOfCreateChat => for {
          (createChatDTO, viewerUserChatId) <- futureOfCreateChat
          optChatPreview <- fillChat(createChatDTO.chatId.value, viewerInfo, viewerUserChatId, mailbox)
          optDeletedChatPreview <- optChatPreview match {
            case Some(chatPreview) =>
              val delete = genBoolean.sample.get

              if (delete) chatsRep.moveChatToTrash(chatPreview.chatId, viewerInfo.userId).map(_ =>
                mailbox match {
                  case Trash => Some(chatPreview)
                  case _ => None
                })
              else Future.successful(mailbox match {
                case Trash => None
                case _ => Some(chatPreview)
              })

            case None => Future.successful(None)
          }

        } yield optDeletedChatPreview)).map(_.flatten)*/

  //endregion

  "SlickChatsRepository#getChatsPreview" should {
    "be valid in [Test-1: 1 Chat, 1 Email, Only From, Drafts]" in {

      for {
        (expectedChatPreview, viewerId) <- db.run(for {
          viewerInfo <- newUser

          createChatDTO = genCreateChatDTOPost.sample.value

          newChat <- chatsRep.postChatAction(createChatDTO, viewerInfo.userId)
          date <- EmailsTable.all.filter(_.emailId === newChat.email.emailId.value)
            .map(_.date).result.headOption

        } yield (ChatPreview(newChat.chatId.value, newChat.subject.value, viewerInfo.address,
          date.value, createChatDTO.email.body.value), viewerInfo.userId))

        chatPreviewSeq <- chatsRep.getChatsPreview(Drafts, viewerId)

      } yield chatPreviewSeq mustBe Seq(expectedChatPreview)

    }

    "be valid in [Test-2: 1 Chat, 1 Email, Only To, Inbox]" in {

      for {
        (expectedChatPreview, viewerId) <- db.run(for {
          viewerInfo <- newUser
          senderInfo <- newUser

          baseCreateEmailDTO = genCreateEmailDTOPost.sample.value

          createEmailDTO = baseCreateEmailDTO.copy(to =
            Some(baseCreateEmailDTO.to.getOrElse(Set.empty[String]) + viewerInfo.address))

          createChatDTO = genCreateChatDTOPost.sample.value.copy(email = createEmailDTO)

          newChat <- chatsRep.postChatAction(createChatDTO, senderInfo.userId)
          date <- EmailsTable.all.filter(_.emailId === newChat.email.emailId.value)
            .map(_.date).result.headOption

          _ <- DBIO.seq(EmailsTable.all.filter(_.emailId === newChat.email.emailId.value)
            .map(_.sent)
            .update(1), UserChatsTable.all += UserChatRow(genUUID.sample.value, viewerInfo.userId, newChat.chatId.value,
            1, 0, 0, 0))

        } yield (ChatPreview(newChat.chatId.value, newChat.subject.value, senderInfo.address,
          date.value, createChatDTO.email.body.value), viewerInfo.userId))

        chatPreviewSeq <- chatsRep.getChatsPreview(Inbox, viewerId)

      } yield chatPreviewSeq mustBe Seq(expectedChatPreview)

    }

    "be valid in [Test-3: 1 Chat, 1 Email, From OR To, Drafts]" in {

      for {
        (expectedChatsPreview, viewerId) <- db.run(
          for {
            viewerInfo <- newUser

            (newChat, viewerUserChatId) <- {
              println("IT BEGINS  ", List.fill(170)("/").toString())
              println(List.fill(170)("/").toString())
              println(List.fill(170)("/").toString())
              println("The USer InfO IS", viewerInfo)
              newChat(viewerInfo.userId)
            }

            participantType = {
              println("THIS IS THE USER_CHATS_TABLE WITH A BRAND NEW CHAT", Await.result(db.run(UserChatsTable.all.result), Duration.Inf))
              val a = genParticipantTypeTest3.sample.value
              println("THIS IS THE RELATION TYPE", a)
              a
            }

            sent = false

            chatpreview <- insertEmailTest4(participantType, newChat.chatId.value, viewerInfo, viewerUserChatId, sent)

            chatspreview = if (participantType.contains(From)) Seq(chatpreview)
            else Seq.empty[ChatPreview]

          } yield (chatspreview, viewerInfo.userId))

        chatPreviewSeq <- chatsRep.getChatsPreview(Drafts, viewerId)

      } yield chatPreviewSeq mustBe expectedChatsPreview

    }

    "be valid in [Test-4-A: 1 Chat, 1 Email, NOT Overseeing, Inbox]" in {

      for {
        (expectedChatsPreview, viewerId) <- db.run(
          for {
            viewerInfo <- newUser

            (newChat, viewerUserChatId) <- {
              println("IT BEGINS  ", List.fill(170)("/").toString())
              println(List.fill(170)("/").toString())
              println(List.fill(170)("/").toString())
              println("The USer InfO IS", viewerInfo)
              newChat(viewerInfo.userId)
            }

            participantType = {
              println("THIS IS THE USER_CHATS_TABLE WITH A BRAND NEW CHAT", Await.result(db.run(UserChatsTable.all.result), Duration.Inf))
              val a = genParticipantTypeTest3.sample.value
              println("THIS IS THE RELATION TYPE", a)
              a
            }

            sent = genBoolean.sample.value

            trash = genBoolean.sample.value

            chatpreview <- {
              println("SENT IS", sent)
              println("TRASH IS", trash)
              insertEmailTest4(participantType, newChat.chatId.value, viewerInfo, viewerUserChatId, sent)
            }

            _ <- if (trash) chatsRep.moveChatToTrashAction(newChat.chatId.value, viewerInfo.userId)
            else DBIO.successful(())

            chatspreview = if (participantIsReceivingTest4(participantType) && sent && !trash)
              Seq(chatpreview)

            else Seq.empty[ChatPreview]
          } yield (chatspreview, viewerInfo.userId))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, viewerId)

      } yield chatsPreview mustBe expectedChatsPreview

    }

    "be valid in [Test-4-B: 1 Chat, 1 Email, NOT Overseeing, Sent]" in {

      for {
        (expectedChatsPreview, viewerId) <- db.run(
          for {
            viewerInfo <- newUser

            (newChat, viewerUserChatId) <- {
              println("IT BEGINS  ", List.fill(170)("/").toString())
              println(List.fill(170)("/").toString())
              println(List.fill(170)("/").toString())
              println("The USer InfO IS", viewerInfo)
              newChat(viewerInfo.userId)
            }

            participantType = {
              println("THIS IS THE USER_CHATS_TABLE WITH A BRAND NEW CHAT", Await.result(db.run(UserChatsTable.all.result), Duration.Inf))
              val a = genParticipantTypeTest3.sample.value
              println("THIS IS THE RELATION TYPE", a)
              a
            }

            sent = genBoolean.sample.value

            trash = genBoolean.sample.value

            chatpreview <- {
              println("SENT IS", sent)
              println("TRASH IS", trash)
              insertEmailTest4(participantType, newChat.chatId.value, viewerInfo, viewerUserChatId, sent)
            }

            _ <- if (trash) chatsRep.moveChatToTrashAction(newChat.chatId.value, viewerInfo.userId)
            else DBIO.successful(())

            chatspreview = if (participantType.contains(From) && sent && !trash) Seq(chatpreview)
            else Seq.empty[ChatPreview]

          } yield (chatspreview, viewerInfo.userId))

        chatsPreview <- chatsRep.getChatsPreview(Sent, viewerId)

      } yield chatsPreview mustBe expectedChatsPreview

    }

    "be valid in [Test-4-C: 1 Chat, 1 Email, NOT Overseeing, Drafts]" in {

      for {
        (expectedChatsPreview, viewerId) <- db.run(
          for {
            viewerInfo <- newUser

            (newChat, viewerUserChatId) <- {
              println("IT BEGINS  ", List.fill(170)("/").toString())
              println(List.fill(170)("/").toString())
              println(List.fill(170)("/").toString())
              println("The USer InfO IS", viewerInfo)
              newChat(viewerInfo.userId)
            }

            participantType = {
              println("THIS IS THE USER_CHATS_TABLE WITH A BRAND NEW CHAT", Await.result(db.run(UserChatsTable.all.result), Duration.Inf))
              val a = genParticipantTypeTest3.sample.value
              println("THIS IS THE RELATION TYPE", a)
              a
            }

            sent = genBoolean.sample.value

            trash = genBoolean.sample.value

            chatpreview <- {
              println("SENT IS", sent)
              println("TRASH IS", trash)
              insertEmailTest4(participantType, newChat.chatId.value, viewerInfo, viewerUserChatId, sent)
            }

            _ <- if (trash) chatsRep.moveChatToTrashAction(newChat.chatId.value, viewerInfo.userId)
            else DBIO.successful(())

            chatspreview = if (participantType.contains(From) && !sent && !trash) Seq(chatpreview)
            else Seq.empty[ChatPreview]

          } yield (chatspreview, viewerInfo.userId))

        chatsPreview <- chatsRep.getChatsPreview(Drafts, viewerId)

      } yield chatsPreview mustBe expectedChatsPreview

    }

    "be valid in [Test-4-D: 1 Chat, 1 Email, NOT Overseeing, Trash]" in {

      for {
        (expectedChatsPreview, viewerId) <- db.run(
          for {
            viewerInfo <- newUser

            (newChat, viewerUserChatId) <- {
              println("IT BEGINS  ", List.fill(170)("/").toString())
              println(List.fill(170)("/").toString())
              println(List.fill(170)("/").toString())
              println("The USer InfO IS", viewerInfo)
              newChat(viewerInfo.userId)
            }

            participantType = {
              println("THIS IS THE USER_CHATS_TABLE WITH A BRAND NEW CHAT", Await.result(db.run(UserChatsTable.all.result), Duration.Inf))
              val a = genParticipantTypeTest3.sample.value
              println("THIS IS THE RELATION TYPE", a)
              a
            }

            sent = genBoolean.sample.value

            trash = genBoolean.sample.value

            chatpreview <- {
              println("SENT IS", sent)
              println("TRASH IS", trash)
              insertEmailTest4(participantType, newChat.chatId.value, viewerInfo, viewerUserChatId, sent)
            }

            _ <- if (trash) chatsRep.moveChatToTrashAction(newChat.chatId.value, viewerInfo.userId)
            else DBIO.successful(())

            chatspreview = if (((participantIsReceivingTest4(participantType) && sent)
              || participantType.contains(From)) && trash) Seq(chatpreview)
            else Seq.empty[ChatPreview]

          } yield (chatspreview, viewerInfo.userId))

        chatsPreview <- chatsRep.getChatsPreview(Trash, viewerId)

      } yield chatsPreview mustBe expectedChatsPreview

    }

    "be valid in [Test-5-A: 1 Chat, Many Emails, NOT Overseeing, Inbox]" in {

      for {
        (expectedChatsPreview, viewerId) <- db.run(
          for {
            viewerInfo <- newUser

            (newChat, viewerUserChatId) <- {
              println("IT BEGINS  ", List.fill(170)("/").toString())
              println(List.fill(170)("/").toString())
              println(List.fill(170)("/").toString())
              println("The USer InfO IS", viewerInfo)
              newChat(viewerInfo.userId)
            }

            trash = genBoolean.sample.value

            optChatspreview <- fillChat(newChat.chatId.value, viewerInfo, viewerUserChatId, Inbox)

            _ <- if (trash) chatsRep.moveChatToTrashAction(newChat.chatId.value, viewerInfo.userId)
            else DBIO.successful(())

            chatspreview = if (trash || optChatspreview.isEmpty) Seq.empty[ChatPreview]
            else Seq(optChatspreview.value)

          } yield (chatspreview, viewerInfo.userId))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, viewerId)

      } yield chatsPreview mustBe expectedChatsPreview

    }

    /*
    "be valid in [Test-5-B: 1 Chat, Many Emails, NOT Overseeing, Sent]" in {
      for {
        viewerInfo <- newUser
        (newChat, viewerUserChatId) <- {
          println("IT BEGINS  ", List.fill(170)("/").toString())
          println(List.fill(170)("/").toString())
          println(List.fill(170)("/").toString())
          println("The USer InfO IS", viewerInfo)
          newChat(viewerInfo.userId)
        }

        trash = genBoolean.sample.value

        optChatspreview <- fillChat(newChat.chatId.value, viewerInfo, viewerUserChatId, Sent)

        _ <- if (trash) chatsRep.moveChatToTrash(newChat.chatId.value, viewerInfo.userId)
        else Future.successful(())

        seq <- chatsRep.getChatsPreview(Sent, viewerInfo.userId)

        chatspreview = if (trash || optChatspreview.isEmpty) Seq.empty[ChatPreview]
        else Seq(optChatspreview.value)

      } yield seq mustBe chatspreview
    }

    "be valid in [Test-5-C: 1 Chat, Many Emails, NOT Overseeing, Drafts]" in {
      for {
        viewerInfo <- newUser
        (newChat, viewerUserChatId) <- {
          println("IT BEGINS  ", List.fill(170)("/").toString())
          println(List.fill(170)("/").toString())
          println(List.fill(170)("/").toString())
          println("The USer InfO IS", viewerInfo)
          newChat(viewerInfo.userId)
        }

        trash = genBoolean.sample.value

        optChatspreview <- fillChat(newChat.chatId.value, viewerInfo, viewerUserChatId, Drafts)

        _ <- if (trash) chatsRep.moveChatToTrash(newChat.chatId.value, viewerInfo.userId)
        else Future.successful(())

        seq <- chatsRep.getChatsPreview(Drafts, viewerInfo.userId)

        chatspreview = if (trash || optChatspreview.isEmpty) Seq.empty[ChatPreview]
        else Seq(optChatspreview.value)

      } yield seq mustBe chatspreview
    }

    "be valid in [Test-5-D: 1 Chat, Many Emails, NOT Overseeing, Trash]" in {
      for {
        viewerInfo <- newUser
        (newChat, viewerUserChatId) <- {
          println("IT BEGINS  ", List.fill(170)("/").toString())
          println(List.fill(170)("/").toString())
          println(List.fill(170)("/").toString())
          println("The USer InfO IS", viewerInfo)
          newChat(viewerInfo.userId)
        }

        trash = genBoolean.sample.value

        optChatspreview <- fillChat(newChat.chatId.value, viewerInfo, viewerUserChatId, Trash)

        _ <- if (trash) chatsRep.moveChatToTrash(newChat.chatId.value, viewerInfo.userId)
        else Future.successful(())

        seq <- chatsRep.getChatsPreview(Trash, viewerInfo.userId)

        chatspreview = if (!trash || optChatspreview.isEmpty) Seq.empty[ChatPreview]
        else Seq(optChatspreview.value)

      } yield seq mustBe chatspreview
    }

    "be valid in [Test-6-A: Many Chats, Many Emails, NOT Overseeing, Inbox]" in {
      for {
        viewerInfo <- newUser

        testChatspreview <- {
          println("IT BEGINS  ", List.fill(170)("/").toString())
          println(List.fill(170)("/").toString())
          println(List.fill(170)("/").toString())
          println("THIS IS THE VIEWER INFO", viewerInfo)
          makeChats(viewerInfo, Inbox)
        }

        chatspreview <- chatsRep.getChatsPreview(Inbox, viewerInfo.userId)

      } yield chatspreview mustBe testChatspreview
    }*/

    /*"be valid for User: 2 Mailbox: Inbox" in {

      val chatsPreview = chatsRep.getChatsPreview(Inbox, "adcd6348-658a-4866-93c5-7e6d32271d8d")

      chatsPreview.map(_ mustBe Seq(
        ChatPreview("303c2b72-304e-4bac-84d7-385acb64a616", "Vencimento", "joao@mail.com", "2019-06-27 11:01:00", "Sim!"),
        ChatPreview("83fa0c9a-1833-4a50-95ac-53e25a2d21bf", "Laser Tag Quarta-feira", "valter@mail.com", "2019-06-19 11:04:00", "18h00"),
        ChatPreview("b87041c7-9044-41a0-99d7-666ce71bbe8d", "Projeto Oversite2", "valter@mail.com", "2019-06-17 10:02:00", "Scrum room")))
    }

    "be valid for User: 3 Mailbox: Inbox" in {

      val chatsPreview = chatsRep.getChatsPreview(Inbox, "25689204-5a8e-453d-bfbc-4180ff0f97b9")

      chatsPreview.map(_ mustBe Seq(
        ChatPreview("303c2b72-304e-4bac-84d7-385acb64a616", "Vencimento", "joana@mail.com", "2019-06-27 11:03:00", "Já vou resolver o assunto!"),
        ChatPreview("83fa0c9a-1833-4a50-95ac-53e25a2d21bf", "Laser Tag Quarta-feira", "valter@mail.com", "2019-06-19 11:04:00", "18h00"),
        ChatPreview("b87041c7-9044-41a0-99d7-666ce71bbe8d", "Projeto Oversite2", "valter@mail.com", "2019-06-17 10:04:00", "Okay, não há problema."),
        ChatPreview("825ee397-f36e-4023-951e-89d6e43a8e7d", "Location", "joao@mail.com", "2019-06-17 10:00:00", "Where are you?")))
    }

    "be valid for User: 4 Mailbox: Inbox" in {

      val chatsPreview = chatsRep.getChatsPreview(Inbox, "ef63108c-8128-4294-8346-bd9b5143ff22")

      chatsPreview.map(_ mustBe Seq(
        ChatPreview("303c2b72-304e-4bac-84d7-385acb64a616", "Vencimento", "joana@mail.com", "2019-06-27 11:03:00", "Já vou resolver o assunto!"),
        ChatPreview("83fa0c9a-1833-4a50-95ac-53e25a2d21bf", "Laser Tag Quarta-feira", "pedrol@mail.com", "2019-06-19 11:05:00", "Também vou!"),
        ChatPreview("b87041c7-9044-41a0-99d7-666ce71bbe8d", "Projeto Oversite2", "valter@mail.com", "2019-06-17 10:04:00", "Okay, não há problema."),
        ChatPreview("825ee397-f36e-4023-951e-89d6e43a8e7d", "Location", "joao@mail.com", "2019-06-17 10:00:00", "Where are you?")))
    }

    "be valid for User: 5 Mailbox: Inbox" in {

      val chatsPreview = chatsRep.getChatsPreview(Inbox, "e598ee8e-b459-499f-94d1-d4f66d583264")

      chatsPreview.map(_ mustBe Seq(
        ChatPreview("83fa0c9a-1833-4a50-95ac-53e25a2d21bf", "Laser Tag Quarta-feira", "pedroc@mail.com", "2019-06-19 11:06:00", "Talvez vá"),
        ChatPreview("b87041c7-9044-41a0-99d7-666ce71bbe8d", "Projeto Oversite2", "valter@mail.com", "2019-06-17 10:04:00", "Okay, não há problema."),
        ChatPreview("825ee397-f36e-4023-951e-89d6e43a8e7d", "Location", "joao@mail.com", "2019-06-17 10:00:00", "Where are you?")))
    }

    "be valid for User: 6 Mailbox: Inbox" in {

      val chatsPreview = chatsRep.getChatsPreview(Inbox, "261c9094-6261-4704-bfd0-02821c235eff")

      chatsPreview.map(_ mustBe Seq(
        ChatPreview("83fa0c9a-1833-4a50-95ac-53e25a2d21bf", "Laser Tag Quarta-feira", "valter@mail.com", "2019-06-19 11:04:00", "18h00"),
        ChatPreview("b87041c7-9044-41a0-99d7-666ce71bbe8d", "Projeto Oversite2", "valter@mail.com", "2019-06-17 10:04:00", "Okay, não há problema."),
        ChatPreview("825ee397-f36e-4023-951e-89d6e43a8e7d", "Location", "joao@mail.com", "2019-06-17 10:00:00", "Where are you?")))
    }

    "be valid for User: 1 Mailbox: Drafts" in {

      val chatsPreview = chatsRep.getChatsPreview(Drafts, "148a3b1b-8326-466d-8c27-1bd09b8378f3")

      chatsPreview.map(_ mustBe Seq(
        ChatPreview("303c2b72-304e-4bac-84d7-385acb64a616", "Vencimento", "beatriz@mail.com", "2019-06-27 11:04:00", "Okay, obrigada!"),
        ChatPreview("825ee397-f36e-4023-951e-89d6e43a8e7d", "Location", "beatriz@mail.com", "2019-06-17 10:06:00", "Here"),
        ChatPreview("b87041c7-9044-41a0-99d7-666ce71bbe8d", "Projeto Oversite2", "beatriz@mail.com", "2019-06-17 10:05:00", "Estou a chegar!")))
    }

    "be valid for User: 5 Mailbox: Drafts" in {

      val chatsPreview = chatsRep.getChatsPreview(Drafts, "e598ee8e-b459-499f-94d1-d4f66d583264")

      chatsPreview.map(_ mustBe Seq(
        ChatPreview("83fa0c9a-1833-4a50-95ac-53e25a2d21bf", "Laser Tag Quarta-feira", "pedroc@mail.com", "2019-06-19 11:06:00", "Talvez vá")))
    }
  }

  "SlickChatsRepository#getChat" should {
    "return a chat for a user that has received an email and has a draft " +
      "(chat (4) 825ee397-f36e-4023-951e-89d6e43a8e7d, user (1) 148a3b1b-8326-466d-8c27-1bd09b8378f3)" in {

        val chat = chatsRep.getChat("825ee397-f36e-4023-951e-89d6e43a8e7d", "148a3b1b-8326-466d-8c27-1bd09b8378f3")

        val expectedRepositoryResponse: Option[Chat] =
          Some(
            Chat(
              "825ee397-f36e-4023-951e-89d6e43a8e7d", "Location", Set("beatriz@mail.com", "joao@mail.com", "pedroc@mail.com"),
              Set(
                Overseers("beatriz@mail.com", Set("valter@mail.com")),
                Overseers("pedrol@mail.com", Set("rui@mail.com"))),
              Seq(
                Email("42508cff-a4cf-47e4-9b7d-db91e010b87a", "joao@mail.com", Set("beatriz@mail.com"), Set(), Set("pedroc@mail.com"),
                  "Where are you?", "2019-06-17 10:00:00", 1, Set()),
                Email("fe4ff891-144a-4f61-af35-6d4a5ec76314", "beatriz@mail.com", Set("joao@mail.com"), Set(), Set(),
                  "Here", "2019-06-17 10:06:00", 0, Set("b8c313cc-90a1-4f2f-81c6-e61a64fb0b16")))))

        chat.map(_ mustBe expectedRepositoryResponse)
      }

    "return a chat for a user that sent an email (with a bcc) " +
      "(chat (4) 825ee397-f36e-4023-951e-89d6e43a8e7d, user (2) adcd6348-658a-4866-93c5-7e6d32271d8d)" in {

        val chat = chatsRep.getChat("825ee397-f36e-4023-951e-89d6e43a8e7d", "adcd6348-658a-4866-93c5-7e6d32271d8d")

        val expectedRepositoryResponse: Option[Chat] =
          Some(
            Chat(
              "825ee397-f36e-4023-951e-89d6e43a8e7d", "Location", Set("beatriz@mail.com", "joao@mail.com", "pedrol@mail.com", "pedroc@mail.com"),
              Set(
                Overseers("beatriz@mail.com", Set("valter@mail.com")),
                Overseers("pedrol@mail.com", Set("rui@mail.com"))),
              Seq(
                Email("42508cff-a4cf-47e4-9b7d-db91e010b87a", "joao@mail.com", Set("beatriz@mail.com"), Set("pedrol@mail.com"), Set("pedroc@mail.com"),
                  "Where are you?", "2019-06-17 10:00:00", 1, Set()))))

        chat.map(_ mustBe expectedRepositoryResponse)
      }

    "return a chat for an overseer of a user (sees what their oversee sees, except for their drafts)" +
      "(chat (4) 825ee397-f36e-4023-951e-89d6e43a8e7d, user (3) 25689204-5a8e-453d-bfbc-4180ff0f97b9)" in {

        val chat = chatsRep.getChat("825ee397-f36e-4023-951e-89d6e43a8e7d", "25689204-5a8e-453d-bfbc-4180ff0f97b9")

        val expectedRepositoryResponse: Option[Chat] =
          Some(
            Chat(
              "825ee397-f36e-4023-951e-89d6e43a8e7d", "Location", Set("beatriz@mail.com", "joao@mail.com", "pedroc@mail.com"),
              Set(
                Overseers("beatriz@mail.com", Set("valter@mail.com")),
                Overseers("pedrol@mail.com", Set("rui@mail.com"))),
              Seq(
                Email("42508cff-a4cf-47e4-9b7d-db91e010b87a", "joao@mail.com", Set("beatriz@mail.com"), Set(), Set("pedroc@mail.com"),
                  "Where are you?", "2019-06-17 10:00:00", 1, Set()))))

        chat.map(_ mustBe expectedRepositoryResponse)
      }

    "return a chat for a user that is a BCC of an email of that chat " +
      "(chat (4) 825ee397-f36e-4023-951e-89d6e43a8e7d, user (4) ef63108c-8128-4294-8346-bd9b5143ff22)" in {

        val chat = chatsRep.getChat("825ee397-f36e-4023-951e-89d6e43a8e7d", "ef63108c-8128-4294-8346-bd9b5143ff22")

        val expectedRepositoryResponse: Option[Chat] =
          Some(
            Chat(
              "825ee397-f36e-4023-951e-89d6e43a8e7d", "Location", Set("beatriz@mail.com", "joao@mail.com", "pedrol@mail.com", "pedroc@mail.com"),
              Set(
                Overseers("beatriz@mail.com", Set("valter@mail.com")),
                Overseers("pedrol@mail.com", Set("rui@mail.com"))),
              Seq(
                Email("42508cff-a4cf-47e4-9b7d-db91e010b87a", "joao@mail.com", Set("beatriz@mail.com"), Set("pedrol@mail.com"), Set("pedroc@mail.com"),
                  "Where are you?", "2019-06-17 10:00:00", 1, Set()))))

        chat.map(_ mustBe expectedRepositoryResponse)
      }

    "return a chat for an overseer of a user that appear as BCC " +
      "(chat (4) 825ee397-f36e-4023-951e-89d6e43a8e7d, user (6) 261c9094-6261-4704-bfd0-02821c235eff)" in {

        val chat = chatsRep.getChat("825ee397-f36e-4023-951e-89d6e43a8e7d", "261c9094-6261-4704-bfd0-02821c235eff")

        val expectedRepositoryResponse: Option[Chat] =
          Some(
            Chat(
              "825ee397-f36e-4023-951e-89d6e43a8e7d", "Location", Set("beatriz@mail.com", "joao@mail.com", "pedrol@mail.com", "pedroc@mail.com"),
              Set(
                Overseers("beatriz@mail.com", Set("valter@mail.com")),
                Overseers("pedrol@mail.com", Set("rui@mail.com"))),
              Seq(
                Email("42508cff-a4cf-47e4-9b7d-db91e010b87a", "joao@mail.com", Set("beatriz@mail.com"), Set("pedrol@mail.com"), Set("pedroc@mail.com"),
                  "Where are you?", "2019-06-17 10:00:00", 1, Set()))))

        chat.map(_ mustBe expectedRepositoryResponse)
      }

    "NOT return a chat for a user that does not exist " +
      "(chat (4) 825ee397-f36e-4023-951e-89d6e43a8e7d, user with random UUID)" in {

        val chat = chatsRep.getChat("825ee397-f36e-4023-951e-89d6e43a8e7d", newUUID)

        //val expectedRepositoryResponse: Option[Chat] = NONE

        chat.map(_ mustBe None)
      }

    "NOT return a chat that does not exist " +
      "(chat with random UUID, user (1) 148a3b1b-8326-466d-8c27-1bd09b8378f3)" in {

        val chat = chatsRep.getChat(newUUID, "148a3b1b-8326-466d-8c27-1bd09b8378f3")

        //val expectedRepositoryResponse: Option[Chat] = NONE

        chat.map(_ mustBe None)
      }

  }

  "SlickChatsRepository#insertAddressIfNotExists" should {
    "insert a new address if it does not exist and return its addressId" in {


      val address = "alice@mail.com"
      for {
        inserted <- db.run(chatsRep.insertAddressIfNotExists(address))
        selected <- db.run(AddressesTable.selectAddressId(address).result.head)
      } yield inserted mustBe selected

      //val debugPrint = db.run(AddressesTable.all.result).map(_.map(a => println(a.addressId + "-" + a.address)))
    }

    "return the addressId if the address already exists in the table" in {


      val address = "beatriz@mail.com"
      for {
        inserted <- db.run(chatsRep.insertAddressIfNotExists(address))
        selected <- db.run(AddressesTable.selectAddressId(address).result.head)
      } yield inserted mustBe selected
    }
  }

  "SlickChatsRepository#postChat+getChat" should {


    val senderUserId = "148a3b1b-8326-466d-8c27-1bd09b8378f3" //beatriz@mail.com
    val receiverUserId = "adcd6348-658a-4866-93c5-7e6d32271d8d" //joao@mail.com

    val createChatDTO =
      CreateChatDTO(
        chatId = None,
        subject = Some("Test Subject"),
        CreateEmailDTO(
          emailId = None,
          from = "beatriz@mail.com",
          to = Some(Set("joao@mail.com", "notuser@mail.com")),
          bcc = Some(Set("spy@mail.com")),
          cc = Some(Set("observer@mail.com")),
          body = Some("Test Body"),
          date = None))

    "create a chat with an email draft for a user and then get the same chat for the same user: results must match" in {

      for {
        postResponse <- chatsRep.postChat(createChatDTO, senderUserId)
        getResponse <- chatsRep.getChat(postResponse.chatId.value, senderUserId)
      } yield getResponse mustBe Some(chatsRep.fromCreateChatDTOtoChatDTO(postResponse))

    }

    "NOT show a chat for a user that is a receiver of the email (to) " +
      "because it was not sent yet (it's a draft, only the owner can see it)" in {

        for {
          postResponse <- chatsRep.postChat(createChatDTO, senderUserId)
          getResponse <- chatsRep.getChat(postResponse.chatId.value, receiverUserId)
        } yield getResponse mustBe None

      }

    "create a chat with an EMPTY draft for a user and then get the same chat for the same user: results must match" in {
      val chatWithEmptyDraft =
        CreateChatDTO(
          chatId = None,
          subject = None,
          CreateEmailDTO(
            emailId = None,
            from = "beatriz@mail.com",
            to = None,
            bcc = None,
            cc = None,
            body = None,
            date = None))

      for {
        postResponse <- chatsRep.postChat(chatWithEmptyDraft, senderUserId)
        getResponse <- chatsRep.getChat(postResponse.chatId.value, senderUserId)
      } yield getResponse mustBe Some(chatsRep.fromCreateChatDTOtoChatDTO(postResponse))

    }

  }

  }

  "SlickChatsRepository#moveChatToTrash" should {
    val chatsRep = new SlickChatsRepository(db)

    val userId = "148a3b1b-8326-466d-8c27-1bd09b8378f3" //beatriz@mail.com

    "remove the user's chat from inbox, sent and draft and move it to trash" in {
      val validChatId = "303c2b72-304e-4bac-84d7-385acb64a616"
      for {
        result <- chatsRep.moveChatToTrash(validChatId, userId)
        optionUserChat <- db.run(UserChatsTable.all.filter(uc => uc.chatId === validChatId && uc.userId === userId).result.headOption)
      } yield inside(optionUserChat) {
        case Some(userChat) =>
          assert(
            result &&
              userChat.inbox === 0 &&
              userChat.sent === 0 &&
              userChat.draft === 0 &&
              userChat.trash === 1)
      }
    }

    "return false if the user does not have a chat with that id" in {
      val invalidChatId = "00000000-0000-0000-0000-000000000000"
      for {
        result <- chatsRep.moveChatToTrash(invalidChatId, userId)
        optionUserChat <- db.run(UserChatsTable.all.filter(uc => uc.chatId === invalidChatId && uc.userId === userId).result.headOption)
      } yield assert(
        !result &&
          optionUserChat === None)
    }


	  */

  }

}
