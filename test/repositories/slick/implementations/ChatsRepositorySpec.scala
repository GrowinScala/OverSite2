package repositories.slick.implementations

import model.dtos.{ CreateChatDTO, CreateEmailDTO }
import model.types.Mailbox._
import model.types.ParticipantType
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
  with BeforeAndAfterEach {

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

  /**
   * Generates a User and registers them into the Database
   * @return A Future of a Tuple of the User's Address and UserId
   */
  def newUser: Future[UserInfo] = {
    val authenticationRep = new SlickAuthenticationRepository(db)
    val userAccessDTO = genUserAccessDTO.sample.value

    for {
      token <- authenticationRep.signUpUser(userAccessDTO)
      userId <- authenticationRep.getUser(token)
    } yield UserInfo(userAccessDTO.address, userId)
  }

  def sendEmailTo(createEmailDTO: CreateEmailDTO, chatId: String,
    receiverUserChatId: String, sent: Boolean): Future[ChatPreview] = {

    for {
      senderInfo <- newUser
      _ <- giveUserChatAccess(senderInfo.userId, chatId)
      optChat <- chatsRep.postEmail(createEmailDTO, chatId, senderInfo.userId)

      chatpreview <- {
        val chat = optChat.value
        val email = chat.email
        (if (sent) sendDraft(email.emailId.value, receiverUserChatId, To)
        else Future.successful(None))
          .map(_ => ChatPreview(chat.chatId.value, chat.subject.value,
            senderInfo.address, email.date.value, email.body.value))
      }
    } yield chatpreview
  }

  def sendDraft(emailId: String, viewerUserChatId: String, participantType: ParticipantType): Future[Boolean] =
    db.run(
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
      } yield mailBoxUpdated == emailSent && emailSent == 1)

  def insertSimpleEmail(participantType: Option[ParticipantType], chatId: String, viewerInfo: UserInfo,
    viewerUserChatId: String, sent: Boolean): Future[ChatPreview] = {
    val createEmailDTO = genCreateEmailDTOPost.sample.value

    participantType match {
      case Some(From) =>
        for {
          optChat <- chatsRep.postEmail(createEmailDTO.copy(from = viewerInfo.address), chatId, viewerInfo.userId)
          sentOptChat <- if (sent) sendDraft(optChat.value.email.emailId.value, viewerUserChatId, From)
            .map(_ => optChat)
          else Future(optChat)

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

  /**
   * Checks if a given user has access to a given chat. If not, gives the user access by creating a row on the
   * UserChatTable with all the Mailboxes turned to 0 and returns the Id of said row. Otherwise returns the Id
   * of the already existing row.
   * @param userId The Id of the user
   * @param chatId The Id of the chat
   * @return A Future containing the Id of the UserChat Row
   */
  def giveUserChatAccess(userId: String, chatId: String) =
    db.run(UserChatsTable.all.filter(userChatRow => userChatRow.userId === userId && userChatRow.chatId === chatId)
      .map(_.userChatId).result.headOption.flatMap {
        case Some(userChatId) => DBIO.successful(userChatId)
        case None =>
          val newUserChatId = genUUID.sample.value
          UserChatsTable.all.+=(UserChatRow(newUserChatId, userId, chatId, 0, 0, 0, 0))
            .andThen(DBIO.successful(newUserChatId))

      })

  def newChat(viewerId: String): Future[(CreateChatDTO, String)] = {

    for {
      userInfo <- newUser
      createChatDTO = genCreateChatDTOPost.sample.value
      newChat <- chatsRep.postChat(createChatDTO, userInfo.userId)
      userChatId <- giveUserChatAccess(viewerId, newChat.chatId.value)
    } yield (newChat, userChatId)
  }

  def fillChat(createChatDTO: CreateChatDTO, viewerInfo: UserInfo) = {

    "To do Later"
  }

  "SlickChatsRepository#getChatsPreview" should {
    "be valid in [Test-1: 1 Chat, 1 Email, Only From, Drafts]" in {

      val authenticationRep = new SlickAuthenticationRepository(db)
      val userAccessDTO = genUserAccessDTO.sample.value

      for {
        token <- authenticationRep.signUpUser(userAccessDTO)
        userId <- authenticationRep.getUser(token)

        createChatDTO = genCreateChatDTOPost.sample.value

        newChat <- chatsRep.postChat(createChatDTO, userId)
        date <- db.run(EmailsTable.all.filter(_.emailId === newChat.email.emailId.value)
          .map(_.date).result.headOption)

        seq <- chatsRep.getChatsPreview(Drafts, userId)
      } yield seq.toList mustBe Seq(ChatPreview(newChat.chatId.value, newChat.subject.value, userAccessDTO.address,
        date.value, createChatDTO.email.body.value))

    }

    "be valid in [Test-2: 1 Chat, 1 Email, Only To, Inbox]" in {

      val authenticationRep = new SlickAuthenticationRepository(db)
      val userAccessDTOFrom = genUserAccessDTO.sample.value
      val userAccessDTOTo = genUserAccessDTO.sample.value

      for {
        tokenFrom <- authenticationRep.signUpUser(userAccessDTOFrom)
        userIdFrom <- authenticationRep.getUser(tokenFrom)

        tokenTo <- authenticationRep.signUpUser(userAccessDTOTo)
        userIdTo <- authenticationRep.getUser(tokenTo)

        createEmailDTO = genCreateEmailDTOPost.sample.value.copy(to = Some(Set(userAccessDTOTo.address)))
        createChatDTO = genCreateChatDTOPost.sample.value.copy(email = createEmailDTO)

        newChat <- chatsRep.postChat(createChatDTO, userIdFrom)
        date <- db.run(EmailsTable.all.filter(_.emailId === newChat.email.emailId.value)
          .map(_.date).result.headOption)

        _ <- db.run(DBIO.seq(EmailsTable.all.filter(_.emailId === newChat.email.emailId.value)
          .map(_.sent)
          .update(1), UserChatsTable.all += UserChatRow(genUUID.sample.value, userIdTo, newChat.chatId.value,
          1, 0, 0, 0)))

        seq <- chatsRep.getChatsPreview(Inbox, userIdTo)
      } yield seq mustBe
        Seq(ChatPreview(newChat.chatId.value, newChat.subject.value, userAccessDTOFrom.address,
          date.value, createChatDTO.email.body.value))
    }

    "be valid in [Test-3: 1 Chat, 1 Email, From OR To, Drafts]" in {

      for {
        viewerInfo <- newUser
        (newChat, viewerUserChatId) <- {
          println("The USer InfO IS", viewerInfo)
          newChat(viewerInfo.userId)
        }
        participantType = {
          println("This is the UserChatsTable with a brand new chat", Await.result(db.run(UserChatsTable.all.result), Duration.Inf))
          val a = genTest3participantType.sample.value
          println("This is the relation type", a)
          a
        }

        sent = false

        chatpreview <- insertSimpleEmail(participantType, newChat.chatId.value, viewerInfo, viewerUserChatId, sent)
        seq <- chatsRep.getChatsPreview(Drafts, viewerInfo.userId)

        chatspreview = if (participantType.contains(From)) Seq(chatpreview)
        else Seq.empty[ChatPreview]

      } yield seq mustBe chatspreview
    }

    "be valid in [Test-4-A: 1 Chat, 1 Email, NOT Overseeing, Inbox]" in {

      for {
        viewerInfo <- newUser
        (newChat, viewerUserChatId) <- {
          println("The USer InfO IS", viewerInfo)
          newChat(viewerInfo.userId)
        }
        participantType = {
          println(
            "This is the UserChatsTable with a brand new chat",
            Await.result(db.run(UserChatsTable.all.result), Duration.Inf))
          val a = genTest4participantType.sample.value
          println("This is the relation type", a)
          a
        }

        sent = genBoolean.sample.value

        chatpreview <- insertSimpleEmail(participantType, newChat.chatId.value, viewerInfo, viewerUserChatId, sent)
        seq <- chatsRep.getChatsPreview(Inbox, viewerInfo.userId)

        chatspreview = if (participantType.contains(From) || !sent) Seq.empty[ChatPreview]
        else Seq(chatpreview)

      } yield seq mustBe chatspreview
    }

    "be valid in [Test-4-B: 1 Chat, 1 Email, NOT Overseeing, Sent]" in {

      for {
        viewerInfo <- newUser
        (newChat, viewerUserChatId) <- {
          println("The USer InfO IS", viewerInfo)
          newChat(viewerInfo.userId)
        }
        participantType = {
          println(
            "This is the UserChatsTable with a brand new chat",
            Await.result(db.run(UserChatsTable.all.result), Duration.Inf))
          val a = genTest4participantType.sample.value
          println("This is the relation type", a)
          a
        }

        sent = genBoolean.sample.value

        chatpreview <- {
          println("SENT IS", sent)
          insertSimpleEmail(participantType, newChat.chatId.value, viewerInfo, viewerUserChatId, sent)
        }
        seq <- chatsRep.getChatsPreview(Sent, viewerInfo.userId)

        chatspreview = if (participantType.contains(From) && sent) Seq(chatpreview)
        else Seq.empty[ChatPreview]

      } yield seq mustBe chatspreview
    }

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

  }*/

  }

}
