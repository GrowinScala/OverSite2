package repositories.slick.implementations

import model.dtos.UpsertEmailDTO
import model.types.Mailbox._
import model.types.ParticipantType._
import model.types.{ Mailbox, ParticipantType }
import org.scalacheck.Gen
import org.scalatest._
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import repositories.dtos.ChatPreview
import repositories.slick.mappings._
import slick.jdbc.MySQLProfile.api._
import utils.TestGenerators._
import model.types.testTypeAliases._

import scala.annotation.tailrec
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

  //region Auxiliary Methods

  //region getChatsPreview#OLD

  def participantIsReceiving(participantType: Option[ParticipantType]): Boolean =
    participantType.contains(To) ||
      participantType.contains(Cc) ||
      participantType.contains(Bcc) ||
      participantType.contains(Overseer)

  def addressRowToEmailAdressRow(emailId: String, chatId: String,
    addressRow: AddressRow, participantType: String): EmailAddressRow =
    genEmailAddressRow(emailId, chatId, addressRow.addressId, participantType).sample.value

  def participantsToEmailAddressRows(emailId: String, chatId: String,
    participantsAddressRows: ParticipantsAddressRows): List[EmailAddressRow] =
    addressRowToEmailAdressRow(emailId, chatId, participantsAddressRows.from, "from") +:
      participantsAddressRows.to.map(addressRowToEmailAdressRow(emailId, chatId, _, "to")) :::
      participantsAddressRows.cc.map(addressRowToEmailAdressRow(emailId, chatId, _, "cc")) :::
      participantsAddressRows.bcc.map(addressRowToEmailAdressRow(emailId, chatId, _, "bcc"))

  def participantsToAddressRows(participantsAddressRows: ParticipantsAddressRows): List[AddressRow] =
    participantsAddressRows.from +: participantsAddressRows.to ::: participantsAddressRows.cc :::
      participantsAddressRows.bcc

  def fillChatTest5(chatRow: ChatRow, viewerAddressRow: AddressRow, baseUserChatRow: UserChatRow): ChatCreationData = {
    val chatSize = Gen.choose(1, 10).sample.value

    @tailrec
    def fillChatRecur(oldChatCreationData: ChatCreationData, chatSize: Int, acc: Int): ChatCreationData = {
      if (acc == chatSize)
        oldChatCreationData
      else {
        val oldUserChatRow = oldChatCreationData.userChatRow
        val oldEmailPreview = oldChatCreationData.emailPreview

        val emailRow = genEmailRow(chatRow.chatId).sample.value
        val sent = emailRow.sent

        val baseParticipantsAddressRows = genParticipantsAddressRows.sample.value
        val viewerParticipantType = genSimpleParticipantType.sample.value

        val participantsAddressRows = viewerParticipantType match {
          case Some(From) => baseParticipantsAddressRows.copy(from = viewerAddressRow)
          case Some(To) => baseParticipantsAddressRows.copy(to =
            viewerAddressRow +: baseParticipantsAddressRows.to)
          case Some(Cc) => baseParticipantsAddressRows.copy(cc =
            viewerAddressRow +: baseParticipantsAddressRows.cc)
          case Some(Bcc) => baseParticipantsAddressRows.copy(bcc =
            viewerAddressRow +: baseParticipantsAddressRows.bcc)
          case _ => baseParticipantsAddressRows
        }

        val emailAddressRows = participantsToEmailAddressRows(
          emailRow.emailId,
          chatRow.chatId, participantsAddressRows)
        val newAddressRows = (participantsToAddressRows(participantsAddressRows) ++ oldChatCreationData.addressRows)
          .distinct

        val newUserChatRow = (viewerParticipantType, sent) match {
          case (Some(From), 1) => oldUserChatRow.copy(sent = 1)
          case (Some(From), 0) => oldUserChatRow.copy(draft = oldUserChatRow.draft + 1)
          case (Some(_), 1) => oldUserChatRow.copy(inbox = 1)
          case _ => oldUserChatRow
        }
        val fromAddress = participantsAddressRows.from.address

        val optionThisChatPreview = if ((participantIsReceiving(viewerParticipantType) && sent == 1) ||
          viewerParticipantType.contains(From))
          Some(ChatPreview(chatRow.chatId, chatRow.subject, fromAddress, emailRow.date, emailRow.body))
        else None

        val thisEmailPreview = optionThisChatPreview.map((emailRow.emailId, _))

        val newEmailPreview = (oldEmailPreview, thisEmailPreview) match {
          case (None, _) => thisEmailPreview
          case (_, None) => oldEmailPreview

          case (Some((oldEmailId, oldChatPreview)), Some((thisEmailId, thisChatPreview))) if oldChatPreview.lastEmailDate == thisChatPreview.lastEmailDate =>
            if (oldEmailId < thisEmailId)
              oldEmailPreview
            else thisEmailPreview

          case (Some((oldEmailId, oldChatPreview)), Some((thisEmailId, thisChatPreview))) =>
            if (oldChatPreview.lastEmailDate > thisChatPreview.lastEmailDate)
              oldEmailPreview
            else thisEmailPreview
        }

        val newChatCreationData = oldChatCreationData.copy(
          emailRows = emailRow +: oldChatCreationData.emailRows,
          userChatRow = newUserChatRow,
          addressRows = newAddressRows,
          emailAddressRows = emailAddressRows ++ oldChatCreationData.emailAddressRows,
          emailPreview = newEmailPreview)

        fillChatRecur(newChatCreationData, chatSize, acc + 1)
      }
    }

    val initChatCreationData = ChatCreationData(baseUserChatRow, List.empty[EmailRow], List.empty[AddressRow],
      List.empty[EmailAddressRow], None)

    fillChatRecur(initChatCreationData, chatSize, 0)
  }

  def updateListHead[T](list: List[T], newHead: T): List[T] =
    list match {
      case Nil => List(newHead)
      case head +: tail => newHead +: tail
    }

  def visibleToMailbox(userChatRow: UserChatRow, mailbox: Mailbox): Boolean = {
    mailbox match {
      case Inbox => userChatRow.inbox == 1
      case Sent => userChatRow.sent == 1
      case Drafts => userChatRow.draft >= 1
      case Trash => userChatRow.trash == 1
    }

  }

  def fillChatTest6(viewerAddressRow: AddressRow, oldDBCreationData: SimpleDBCreationData): SimpleDBCreationData = {
    val chatSize = Gen.choose(1, 10).sample.value

    @tailrec
    def fillChatRecur(oldDBCreationData: SimpleDBCreationData, chatSize: Int, acc: Int): SimpleDBCreationData = {
      if (acc == chatSize)
        oldDBCreationData
      else {
        val chatRow = oldDBCreationData.chatRows.headOption.value
        val oldUserChatRow = oldDBCreationData.userChatRows.headOption.value
        val oldEmailPreview = oldDBCreationData.emailsPreview.headOption.value

        val emailRow = genEmailRow(chatRow.chatId).sample.value
        val sent = emailRow.sent

        val baseParticipantsAddressRows = genParticipantsAddressRows.sample.value
        val viewerParticipantType = genSimpleParticipantType.sample.value

        val participantsAddressRows = viewerParticipantType match {
          case Some(From) => baseParticipantsAddressRows.copy(from = viewerAddressRow)
          case Some(To) => baseParticipantsAddressRows.copy(to =
            viewerAddressRow +: baseParticipantsAddressRows.to)
          case Some(Cc) => baseParticipantsAddressRows.copy(cc =
            viewerAddressRow +: baseParticipantsAddressRows.cc)
          case Some(Bcc) => baseParticipantsAddressRows.copy(bcc =
            viewerAddressRow +: baseParticipantsAddressRows.bcc)
          case None => baseParticipantsAddressRows
          case Some(string) => fail(s""""The string "$string" does not correspont to a ParticipantType""")
        }

        val emailAddressRows = participantsToEmailAddressRows(
          emailRow.emailId,
          chatRow.chatId, participantsAddressRows)
        val newAddressRows = (participantsToAddressRows(participantsAddressRows) ++ oldDBCreationData.addressRows)
          .distinct

        val newUserChatRow = (viewerParticipantType, sent) match {
          case (Some(From), 1) => oldUserChatRow.copy(sent = 1)
          case (Some(From), 0) => oldUserChatRow.copy(draft = oldUserChatRow.draft + 1)
          case (Some(_), 1) => oldUserChatRow.copy(inbox = 1)
          case _ => oldUserChatRow
        }
        val fromAddress = participantsAddressRows.from.address

        val optionThisChatPreview = if ((participantIsReceiving(viewerParticipantType) && sent == 1) ||
          viewerParticipantType.contains(From))
          Some(ChatPreview(chatRow.chatId, chatRow.subject, fromAddress, emailRow.date, emailRow.body))
        else None

        val thisEmailPreview = optionThisChatPreview.map((emailRow.emailId, _))

        val newEmailPreview = (oldEmailPreview, thisEmailPreview) match {
          case (None, _) => thisEmailPreview
          case (_, None) => oldEmailPreview

          case (Some((oldEmailId, oldChatPreview)), Some((thisEmailId, thisChatPreview))) if oldChatPreview.lastEmailDate == thisChatPreview.lastEmailDate =>
            if (oldEmailId < thisEmailId)
              oldEmailPreview
            else thisEmailPreview

          case (Some((oldEmailId, oldChatPreview)), Some((thisEmailId, thisChatPreview))) =>
            if (oldChatPreview.lastEmailDate > thisChatPreview.lastEmailDate)
              oldEmailPreview
            else thisEmailPreview
        }

        val newEmailsPreview = updateListHead(oldDBCreationData.emailsPreview, newEmailPreview)

        val newUserChatRows = updateListHead(oldDBCreationData.userChatRows, newUserChatRow)

        val newDBCreationData = oldDBCreationData.copy(
          userChatRows = newUserChatRows,
          emailRows = emailRow +: oldDBCreationData.emailRows,
          addressRows = newAddressRows,
          emailAddressRows = emailAddressRows ++ oldDBCreationData.emailAddressRows,
          emailsPreview = newEmailsPreview)

        fillChatRecur(newDBCreationData, chatSize, acc + 1)
      }
    }

    fillChatRecur(oldDBCreationData, chatSize, 0)
  }

  def fillDBTest6(viewerAddressRow: AddressRow, viewerUserRow: UserRow, mailbox: Mailbox): SimpleDBCreationData = {
    val dbSize = Gen.choose(1, 10).sample.value

    @tailrec
    def filDBRecur(oldDBCreationData: SimpleDBCreationData, dbSize: Int, acc: Int): SimpleDBCreationData = {
      if (acc == dbSize)
        oldDBCreationData
      else {
        val chatRow = genChatRow.sample.value
        val baseUserChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
        val trash = genBoolean.sample.value

        val filledDBCreationData = fillChatTest6(viewerAddressRow, oldDBCreationData
          .copy(
            chatRows = chatRow +: oldDBCreationData.chatRows,
            userChatRows = baseUserChatRow +: oldDBCreationData.userChatRows,
            emailsPreview = None +: oldDBCreationData.emailsPreview))

        val trashedUserChatRow = filledDBCreationData.userChatRows.headOption.value
          .copy(inbox = 0, sent = 0, draft = 0, trash = 1)

        val trashedDBCreationData = if (trash)
          filledDBCreationData
            .copy(
              userChatRows = updateListHead(
                filledDBCreationData.userChatRows,
                trashedUserChatRow))
        else filledDBCreationData

        val newEmailPreview = (
          trashedDBCreationData.emailsPreview.headOption.value,
          trashedDBCreationData.userChatRows.headOption.value) match {
            case (Some((emailId, chatPreview)), userChatRow) if visibleToMailbox(userChatRow, mailbox) => Some((emailId, chatPreview))
            case _ => None
          }
        val newDBCreationData = trashedDBCreationData
          .copy(emailsPreview = updateListHead(trashedDBCreationData.emailsPreview, newEmailPreview))

        filDBRecur(newDBCreationData, dbSize, acc + 1)
      }
    }

    filDBRecur(
      SimpleDBCreationData(List.empty[ChatRow], List.empty[UserChatRow], List.empty[EmailRow],
        List.empty[AddressRow], List.empty[EmailAddressRow], List.empty[EmailPreview]),
      dbSize, 0)
  }

  def addOversee(chatId: String, overseerId: String, baseParticipantsAddressRows: ParticipantsAddressRows): (ParticipantsAddressRows, Option[OversightInfo]) = {
    val overseeParticipantType = genSimpleParticipantType.sample.value
    val overseeAddressRow = genAddressRow.sample.value
    val overseeUserRow = genUserRow(overseeAddressRow.addressId).sample.value
    val oversightRow = genOversightRow(chatId, overseerId, overseeUserRow.userId).sample.value

    val participantsAddressRows = overseeParticipantType match {
      case Some(From) => baseParticipantsAddressRows.copy(from = overseeAddressRow)
      case Some(To) => baseParticipantsAddressRows.copy(to =
        overseeAddressRow +: baseParticipantsAddressRows.to)
      case Some(Cc) => baseParticipantsAddressRows.copy(cc =
        overseeAddressRow +: baseParticipantsAddressRows.cc)
      case Some(Bcc) => baseParticipantsAddressRows.copy(bcc =
        overseeAddressRow +: baseParticipantsAddressRows.bcc)
      case _ => baseParticipantsAddressRows
    }

    (participantsAddressRows, Some(OversightInfo(overseeAddressRow, overseeUserRow, oversightRow)))
  }

  def fillChat(viewerAddressRow: AddressRow, viewerUserRow: UserRow, oldDBCreationData: DBCreationData): DBCreationData = {
    val chatSize = Gen.choose(1, 10).sample.value

    @tailrec
    def fillChatRecur(oldDBCreationData: DBCreationData, chatSize: Int, acc: Int): DBCreationData = {
      if (acc == chatSize)
        oldDBCreationData
      else {
        val chatRow = oldDBCreationData.chatRows.headOption.value
        val oldUserChatRow = oldDBCreationData.userChatRows.headOption.value
        val oldEmailPreview = oldDBCreationData.emailsPreview.headOption.value

        val emailRow = genEmailRow(chatRow.chatId).sample.value
        val sent = emailRow.sent

        val baseParticipantsAddressRows = genParticipantsAddressRows.sample.value
        val viewerParticipantType = genParticipantType.sample.value

        val (participantsAddressRows, optoversightInfo) = viewerParticipantType match {
          case Some(From) => (baseParticipantsAddressRows.copy(from = viewerAddressRow), None)
          case Some(To) => (baseParticipantsAddressRows.copy(to =
            viewerAddressRow +: baseParticipantsAddressRows.to), None)
          case Some(Cc) => (baseParticipantsAddressRows.copy(cc =
            viewerAddressRow +: baseParticipantsAddressRows.cc), None)
          case Some(Bcc) => (baseParticipantsAddressRows.copy(bcc =
            viewerAddressRow +: baseParticipantsAddressRows.bcc), None)
          case Some(Overseer) => addOversee(chatRow.chatId, viewerUserRow.userId, baseParticipantsAddressRows)
          case _ => (baseParticipantsAddressRows, None)
        }

        val emailAddressRows = participantsToEmailAddressRows(
          emailRow.emailId,
          chatRow.chatId, participantsAddressRows)
        val newAddressRows = (participantsToAddressRows(participantsAddressRows) ++ oldDBCreationData.addressRows)
          .distinct

        val newUserChatRow = (viewerParticipantType, sent) match {
          case (Some(From), 1) => oldUserChatRow.copy(sent = 1)
          case (Some(From), 0) => oldUserChatRow.copy(draft = oldUserChatRow.draft + 1)
          case (Some(_), 1) => oldUserChatRow.copy(inbox = 1)
          case _ => oldUserChatRow
        }
        val fromAddress = participantsAddressRows.from.address

        val optionThisChatPreview = if ((participantIsReceiving(viewerParticipantType) && sent == 1) ||
          viewerParticipantType.contains(From))
          Some(ChatPreview(chatRow.chatId, chatRow.subject, fromAddress, emailRow.date, emailRow.body))
        else None

        val thisEmailPreview = optionThisChatPreview.map((emailRow.emailId, _))

        val newEmailPreview = (oldEmailPreview, thisEmailPreview) match {
          case (None, _) => thisEmailPreview
          case (_, None) => oldEmailPreview

          case (Some((oldEmailId, oldChatPreview)), Some((thisEmailId, thisChatPreview))) if oldChatPreview.lastEmailDate == thisChatPreview.lastEmailDate =>
            if (oldEmailId < thisEmailId)
              oldEmailPreview
            else thisEmailPreview

          case (Some((oldEmailId, oldChatPreview)), Some((thisEmailId, thisChatPreview))) =>
            if (oldChatPreview.lastEmailDate > thisChatPreview.lastEmailDate)
              oldEmailPreview
            else thisEmailPreview
        }

        val newEmailsPreview = updateListHead(oldDBCreationData.emailsPreview, newEmailPreview)

        val newUserChatRows = updateListHead(oldDBCreationData.userChatRows, newUserChatRow)

        val newDBCreationData = oldDBCreationData.copy(
          userChatRows = newUserChatRows,
          emailRows = emailRow +: oldDBCreationData.emailRows,
          addressRows = newAddressRows,
          emailAddressRows = emailAddressRows ++ oldDBCreationData.emailAddressRows,
          emailsPreview = newEmailsPreview,
          oversightInfoList = optoversightInfo +: oldDBCreationData.oversightInfoList)

        fillChatRecur(newDBCreationData, chatSize, acc + 1)
      }
    }

    fillChatRecur(oldDBCreationData, chatSize, 0)
  }

  def filDB(viewerAddressRow: AddressRow, viewerUserRow: UserRow, mailbox: Mailbox): DBCreationData = {
    val dbSize = Gen.choose(1, 10).sample.value

    @tailrec
    def filDBRecur(oldDBCreationData: DBCreationData, dbSize: Int, acc: Int): DBCreationData = {
      if (acc == dbSize)
        oldDBCreationData
      else {
        val chatRow = genChatRow.sample.value
        val baseUserChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
        val trash = genBoolean.sample.value

        val filledDBCreationData = fillChat(viewerAddressRow, viewerUserRow, oldDBCreationData
          .copy(
            chatRows = chatRow +: oldDBCreationData.chatRows,
            userChatRows = baseUserChatRow +: oldDBCreationData.userChatRows,
            emailsPreview = None +: oldDBCreationData.emailsPreview))

        val trashedUserChatRow = filledDBCreationData.userChatRows.headOption.value
          .copy(inbox = 0, sent = 0, draft = 0, trash = 1)

        val trashedDBCreationData = if (trash)
          filledDBCreationData
            .copy(
              userChatRows = updateListHead(
                filledDBCreationData.userChatRows,
                trashedUserChatRow))
        else filledDBCreationData

        val newEmailPreview = (
          trashedDBCreationData.emailsPreview.headOption.value,
          trashedDBCreationData.userChatRows.headOption.value) match {
            case (Some((emailId, chatPreview)), userChatRow) if visibleToMailbox(userChatRow, mailbox) => Some((emailId, chatPreview))
            case _ => None
          }
        val newDBCreationData = trashedDBCreationData
          .copy(emailsPreview = updateListHead(trashedDBCreationData.emailsPreview, newEmailPreview))

        filDBRecur(newDBCreationData, dbSize, acc + 1)
      }
    }

    filDBRecur(
      DBCreationData(List.empty[ChatRow], List.empty[UserChatRow], List.empty[EmailRow],
        List.empty[AddressRow], List.empty[EmailAddressRow], List.empty[EmailPreview],
        List.empty[Option[OversightInfo]]),
      dbSize, 0)
  }
  //endregion

  //region getChatsPreview#NEW

  //endregion

  //endregion

  /*"SlickChatsRepository#getChatsPreview#OLD" should {
    "be valid in [Test-1: 1 Chat, 1 Email, Only From, Drafts]" in {

      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val emailRow = genEmailRow(chatRow.chatId).sample.value.copy(sent = 0)
      val emailAddressesRow = genEmailAddressRow(emailRow.emailId, chatRow.chatId,
        viewerAddressRow.addressId, "from").sample.value
      val userChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
        .copy(draft = 1)

      val expectedChatsPreview = Seq(ChatPreview(chatRow.chatId, chatRow.subject, viewerAddressRow.address,
        emailRow.date, emailRow.body))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all += viewerAddressRow,
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += userChatRow,
          EmailsTable.all += emailRow,
          EmailAddressesTable.all += emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Drafts, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview

    }

    "be valid in [Test-2: 1 Chat, 1 Email, Only To, Inbox]" in {

      val viewerAddressRow = genAddressRow.sample.value
      val senderAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val emailRow = genEmailRow(chatRow.chatId).sample.value.copy(sent = 1)
      val viewerEmailAddressesRow = genEmailAddressRow(emailRow.emailId, chatRow.chatId,
        viewerAddressRow.addressId, "to").sample.value
      val senderEmailAddressesRow = genEmailAddressRow(emailRow.emailId, chatRow.chatId,
        senderAddressRow.addressId, "from").sample.value
      val userChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
        .copy(inbox = 1)

      val expectedChatsPreview = Seq(ChatPreview(chatRow.chatId, chatRow.subject, senderAddressRow.address,
        emailRow.date, emailRow.body))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= Seq(viewerAddressRow, senderAddressRow),
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += userChatRow,
          EmailsTable.all += emailRow,
          EmailAddressesTable.all ++= Seq(viewerEmailAddressesRow, senderEmailAddressesRow)))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview

    }

    "be valid in [Test-3: 1 Chat, 1 Email, From OR To, Drafts]" in {

      val viewerAddressRow = genAddressRow.sample.value
      val otherAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val emailRow = genEmailRow(chatRow.chatId).sample.value.copy(sent = 0)
      val viewerPTisFrom = genBoolean.sample.value
      val baseViewerEmailAddressesRow = genEmailAddressRow(emailRow.emailId, chatRow.chatId,
        viewerAddressRow.addressId, "").sample.value
      val baseOtherEmailAddressesRow = genEmailAddressRow(emailRow.emailId, chatRow.chatId,
        otherAddressRow.addressId, "").sample.value

      val (viewerEmailAddressesRow, otherEmailAddressesRow, fromAddress, draft) =
        if (viewerPTisFrom)
          (
            baseViewerEmailAddressesRow.copy(participantType = "from"),
            baseOtherEmailAddressesRow.copy(participantType = "to"),
            viewerAddressRow.address,
            1)
        else (
          baseViewerEmailAddressesRow.copy(participantType = "to"),
          baseOtherEmailAddressesRow.copy(participantType = "from"),
          otherAddressRow.address,
          0)

      val userChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
        .copy(draft = 1)

      val expectedChatsPreview = if (viewerPTisFrom)
        Seq(ChatPreview(chatRow.chatId, chatRow.subject, fromAddress,
          emailRow.date, emailRow.body))
      else Seq.empty[ChatPreview]

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= Seq(viewerAddressRow, otherAddressRow),
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += userChatRow,
          EmailsTable.all += emailRow,
          EmailAddressesTable.all ++= Seq(viewerEmailAddressesRow, otherEmailAddressesRow)))

        chatsPreview <- chatsRep.getChatsPreview(Drafts, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview

    }

    //region  Test-4: 1 Chat, 1 Email, NOT Overseeing
    "be valid in [Test-4-A: 1 Chat, 1 Email, NOT Overseeing, Inbox]" in {

      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val emailRow = genEmailRow(chatRow.chatId).sample.value
      val sent = emailRow.sent
      val trash = genBoolean.sample.value

      val baseParticipantsAddressRows = genParticipantsAddressRows.sample.value
      val viewerParticipantType = genSimpleParticipantType.sample.value

      val participantsAddressRows = viewerParticipantType match {
        case Some(From) => baseParticipantsAddressRows.copy(from = viewerAddressRow)
        case Some(To) => baseParticipantsAddressRows.copy(to =
          viewerAddressRow +: baseParticipantsAddressRows.to)
        case Some(Cc) => baseParticipantsAddressRows.copy(cc =
          viewerAddressRow +: baseParticipantsAddressRows.cc)
        case Some(Bcc) => baseParticipantsAddressRows.copy(bcc =
          viewerAddressRow +: baseParticipantsAddressRows.bcc)
        case _ => baseParticipantsAddressRows
      }

      val emailAddressRows = participantsToEmailAddressRows(emailRow.emailId, chatRow.chatId, participantsAddressRows)
      val addressRows = participantsToAddressRows(participantsAddressRows)

      val baseUserChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
      val userChatRow = (viewerParticipantType, sent, trash) match {
        case (_, _, true) => baseUserChatRow.copy(trash = 1)
        case (Some(From), 1, _) => baseUserChatRow.copy(sent = 1)
        case (Some(From), 0, _) => baseUserChatRow.copy(draft = 1)
        case (Some(_), 1, _) => baseUserChatRow.copy(inbox = 1)
        case _ => baseUserChatRow
      }
      val fromAddress = participantsAddressRows.from.address

      val expectedChatsPreview = if (participantIsReceiving(viewerParticipantType) && sent == 1 && !trash)
        Seq(ChatPreview(chatRow.chatId, chatRow.subject, fromAddress,
          emailRow.date, emailRow.body))
      else Seq.empty[ChatPreview]

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= addressRows,
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += userChatRow,
          EmailsTable.all += emailRow,
          EmailAddressesTable.all ++= emailAddressRows))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview

    }

    "be valid in [Test-4-B: 1 Chat, 1 Email, NOT Overseeing, Sent]" in {
      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val emailRow = genEmailRow(chatRow.chatId).sample.value
      val sent = emailRow.sent
      val trash = genBoolean.sample.value

      val baseParticipantsAddressRows = genParticipantsAddressRows.sample.value
      val viewerParticipantType = genSimpleParticipantType.sample.value

      val participantsAddressRows = viewerParticipantType match {
        case Some(From) => baseParticipantsAddressRows.copy(from = viewerAddressRow)
        case Some(To) => baseParticipantsAddressRows.copy(to =
          viewerAddressRow +: baseParticipantsAddressRows.to)
        case Some(Cc) => baseParticipantsAddressRows.copy(cc =
          viewerAddressRow +: baseParticipantsAddressRows.cc)
        case Some(Bcc) => baseParticipantsAddressRows.copy(bcc =
          viewerAddressRow +: baseParticipantsAddressRows.bcc)
        case _ => baseParticipantsAddressRows
      }

      val emailAddressRows = participantsToEmailAddressRows(emailRow.emailId, chatRow.chatId, participantsAddressRows)
      val addressRows = participantsToAddressRows(participantsAddressRows)

      val baseUserChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
      val userChatRow = (viewerParticipantType, sent, trash) match {
        case (_, _, true) => baseUserChatRow.copy(trash = 1)
        case (Some(From), 1, _) => baseUserChatRow.copy(sent = 1)
        case (Some(From), 0, _) => baseUserChatRow.copy(draft = 1)
        case (Some(_), 1, _) => baseUserChatRow.copy(inbox = 1)
        case _ => baseUserChatRow
      }
      val fromAddress = participantsAddressRows.from.address

      val expectedChatsPreview = if (viewerParticipantType.contains(From) && sent == 1 && !trash)
        Seq(ChatPreview(chatRow.chatId, chatRow.subject, fromAddress,
          emailRow.date, emailRow.body))
      else Seq.empty[ChatPreview]

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= addressRows,
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += userChatRow,
          EmailsTable.all += emailRow,
          EmailAddressesTable.all ++= emailAddressRows))

        chatsPreview <- chatsRep.getChatsPreview(Sent, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview

    }

    "be valid in [Test-4-C: 1 Chat, 1 Email, NOT Overseeing, Drafts]" in {

      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val emailRow = genEmailRow(chatRow.chatId).sample.value
      val sent = emailRow.sent
      val trash = genBoolean.sample.value

      val baseParticipantsAddressRows = genParticipantsAddressRows.sample.value
      val viewerParticipantType = genSimpleParticipantType.sample.value

      val participantsAddressRows = viewerParticipantType match {
        case Some(From) => baseParticipantsAddressRows.copy(from = viewerAddressRow)
        case Some(To) => baseParticipantsAddressRows.copy(to =
          viewerAddressRow +: baseParticipantsAddressRows.to)
        case Some(Cc) => baseParticipantsAddressRows.copy(cc =
          viewerAddressRow +: baseParticipantsAddressRows.cc)
        case Some(Bcc) => baseParticipantsAddressRows.copy(bcc =
          viewerAddressRow +: baseParticipantsAddressRows.bcc)
        case _ => baseParticipantsAddressRows

      }

      val emailAddressRows = participantsToEmailAddressRows(emailRow.emailId, chatRow.chatId, participantsAddressRows)
      val addressRows = participantsToAddressRows(participantsAddressRows)

      val baseUserChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
      val userChatRow = (viewerParticipantType, sent, trash) match {
        case (_, _, true) => baseUserChatRow.copy(trash = 1)
        case (Some(From), 1, _) => baseUserChatRow.copy(sent = 1)
        case (Some(From), 0, _) => baseUserChatRow.copy(draft = 1)
        case (Some(_), 1, _) => baseUserChatRow.copy(inbox = 1)
        case _ => baseUserChatRow
      }
      val fromAddress = participantsAddressRows.from.address

      val expectedChatsPreview = if (viewerParticipantType.contains(From) && sent == 0 && !trash)
        Seq(ChatPreview(chatRow.chatId, chatRow.subject, fromAddress,
          emailRow.date, emailRow.body))
      else Seq.empty[ChatPreview]

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= addressRows,
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += userChatRow,
          EmailsTable.all += emailRow,
          EmailAddressesTable.all ++= emailAddressRows))

        chatsPreview <- chatsRep.getChatsPreview(Drafts, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview

    }

    "be valid in [Test-4-D: 1 Chat, 1 Email, NOT Overseeing, Trash]" in {

      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val emailRow = genEmailRow(chatRow.chatId).sample.value
      val sent = emailRow.sent
      val trash = genBoolean.sample.value

      val baseParticipantsAddressRows = genParticipantsAddressRows.sample.value
      val viewerParticipantType = genSimpleParticipantType.sample.value

      val participantsAddressRows = viewerParticipantType match {
        case Some(From) => baseParticipantsAddressRows.copy(from = viewerAddressRow)
        case Some(To) => baseParticipantsAddressRows.copy(to =
          viewerAddressRow +: baseParticipantsAddressRows.to)
        case Some(Cc) => baseParticipantsAddressRows.copy(cc =
          viewerAddressRow +: baseParticipantsAddressRows.cc)
        case Some(Bcc) => baseParticipantsAddressRows.copy(bcc =
          viewerAddressRow +: baseParticipantsAddressRows.bcc)
        case _ => baseParticipantsAddressRows

      }

      val emailAddressRows = participantsToEmailAddressRows(emailRow.emailId, chatRow.chatId, participantsAddressRows)
      val addressRows = participantsToAddressRows(participantsAddressRows)

      val baseUserChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
      val userChatRow = (viewerParticipantType, sent, trash) match {
        case (_, _, true) => baseUserChatRow.copy(trash = 1)
        case (Some(From), 1, _) => baseUserChatRow.copy(sent = 1)
        case (Some(From), 0, _) => baseUserChatRow.copy(draft = 1)
        case (Some(_), 1, _) => baseUserChatRow.copy(inbox = 1)
        case _ => baseUserChatRow
      }
      val fromAddress = participantsAddressRows.from.address

      val expectedChatsPreview = if (((participantIsReceiving(viewerParticipantType) && sent == 1) ||
        viewerParticipantType.contains(From)) && trash)
        Seq(ChatPreview(chatRow.chatId, chatRow.subject, fromAddress,
          emailRow.date, emailRow.body))
      else Seq.empty[ChatPreview]

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= addressRows,
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += userChatRow,
          EmailsTable.all += emailRow,
          EmailAddressesTable.all ++= emailAddressRows))

        chatsPreview <- chatsRep.getChatsPreview(Trash, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview

    }
    //endregion

    //region Test-5: 1 Chat, Many Emails, NOT Overseeing
    "be valid in [Test-5-A: 1 Chat, Many Emails, NOT Overseeing, Inbox]" in {

      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val baseUserChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
      val trash = genBoolean.sample.value

      val chatCreationData = fillChatTest5(chatRow, viewerAddressRow, baseUserChatRow)

      val trashedChatCreationData = if (trash)
        chatCreationData
          .copy(
            userChatRow = chatCreationData.userChatRow.copy(inbox = 0, sent = 0, draft = 0, trash = 1))
      else chatCreationData

      val expectedChatsPreview = (trashedChatCreationData.emailPreview, trashedChatCreationData.userChatRow) match {
        case (Some((_, chatPreview)), userChatRow) if userChatRow.inbox == 1 => Seq(chatPreview)
        case _ => Seq.empty[ChatPreview]
      }

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= trashedChatCreationData.addressRows,
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += trashedChatCreationData.userChatRow,
          EmailsTable.all ++= trashedChatCreationData.emailRows,
          EmailAddressesTable.all ++= trashedChatCreationData.emailAddressRows))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview

    }

    "be valid in [Test-5-B: 1 Chat, Many Emails, NOT Overseeing, Sent]" in {
      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val baseUserChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
      val trash = genBoolean.sample.value

      val chatCreationData = fillChatTest5(chatRow, viewerAddressRow, baseUserChatRow)

      val trashedChatCreationData = if (trash)
        chatCreationData
          .copy(
            userChatRow = chatCreationData.userChatRow.copy(inbox = 0, sent = 0, draft = 0, trash = 1))
      else chatCreationData

      val expectedChatsPreview = (trashedChatCreationData.emailPreview, trashedChatCreationData.userChatRow) match {
        case (Some((_, chatPreview)), userChatRow) if userChatRow.sent == 1 => Seq(chatPreview)
        case _ => Seq.empty[ChatPreview]
      }

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= trashedChatCreationData.addressRows,
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += trashedChatCreationData.userChatRow,
          EmailsTable.all ++= trashedChatCreationData.emailRows,
          EmailAddressesTable.all ++= trashedChatCreationData.emailAddressRows))

        chatsPreview <- chatsRep.getChatsPreview(Sent, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "be valid in [Test-5-C: 1 Chat, Many Emails, NOT Overseeing, Drafts]" in {
      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val baseUserChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
      val trash = genBoolean.sample.value

      val chatCreationData = fillChatTest5(chatRow, viewerAddressRow, baseUserChatRow)

      val trashedChatCreationData = if (trash)
        chatCreationData
          .copy(
            userChatRow = chatCreationData.userChatRow.copy(inbox = 0, sent = 0, draft = 0, trash = 1))
      else chatCreationData

      val expectedChatsPreview = (trashedChatCreationData.emailPreview, trashedChatCreationData.userChatRow) match {
        case (Some((_, chatPreview)), userChatRow) if userChatRow.draft >= 1 => Seq(chatPreview)
        case _ => Seq.empty[ChatPreview]
      }

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= trashedChatCreationData.addressRows,
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += trashedChatCreationData.userChatRow,
          EmailsTable.all ++= trashedChatCreationData.emailRows,
          EmailAddressesTable.all ++= trashedChatCreationData.emailAddressRows))

        chatsPreview <- chatsRep.getChatsPreview(Drafts, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "be valid in [Test-5-D: 1 Chat, Many Emails, NOT Overseeing, Trash]" in {
      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val baseUserChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
      val trash = genBoolean.sample.value

      val chatCreationData = fillChatTest5(chatRow, viewerAddressRow, baseUserChatRow)

      val trashedChatCreationData = if (trash)
        chatCreationData.copy(userChatRow = chatCreationData.userChatRow
          .copy(inbox = 0, sent = 0, draft = 0, trash = 1))
      else chatCreationData

      val expectedChatsPreview = (trashedChatCreationData.emailPreview, trashedChatCreationData.userChatRow) match {
        case (Some((_, chatPreview)), userChatRow) if userChatRow.trash == 1 => Seq(chatPreview)
        case _ => Seq.empty[ChatPreview]
      }

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= trashedChatCreationData.addressRows,
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += trashedChatCreationData.userChatRow,
          EmailsTable.all ++= trashedChatCreationData.emailRows,
          EmailAddressesTable.all ++= trashedChatCreationData.emailAddressRows))

        chatsPreview <- chatsRep.getChatsPreview(Trash, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }
    //endregion
    //region Test-6: Many Chats, Many Emails, NOT Overseeing
    "be valid in [Test-6-A: Many Chats, Many Emails, NOT Overseeing, Inbox]" in {
      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value

      val dbCreationData = fillDBTest6(viewerAddressRow, viewerUserRow, Inbox)

      val expectedChatsPreview = dbCreationData.emailsPreview.flatten.map(_._2)
        .sortBy(chatPreview =>
          (chatPreview.lastEmailDate, chatPreview.contentPreview, chatPreview.lastAddress))(
          Ordering.Tuple3(Ordering.String.reverse, Ordering.String, Ordering.String))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= dbCreationData.addressRows,
          ChatsTable.all ++= dbCreationData.chatRows,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all ++= dbCreationData.userChatRows,
          EmailsTable.all ++= dbCreationData.emailRows,
          EmailAddressesTable.all ++= dbCreationData.emailAddressRows))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview

    }

    "be valid in [Test-6-B: Many Chats, Many Emails, NOT Overseeing, Sent]" in {
      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value

      val dbCreationData = fillDBTest6(viewerAddressRow, viewerUserRow, Sent)

      val expectedChatsPreview = dbCreationData.emailsPreview.flatten.map(_._2)
        .sortBy(chatPreview =>
          (chatPreview.lastEmailDate, chatPreview.contentPreview, chatPreview.lastAddress))(
          Ordering.Tuple3(Ordering.String.reverse, Ordering.String, Ordering.String))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= dbCreationData.addressRows,
          ChatsTable.all ++= dbCreationData.chatRows,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all ++= dbCreationData.userChatRows,
          EmailsTable.all ++= dbCreationData.emailRows,
          EmailAddressesTable.all ++= dbCreationData.emailAddressRows))

        chatsPreview <- chatsRep.getChatsPreview(Sent, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview

    }

    "be valid in [Test-6-C: Many Chats, Many Emails, NOT Overseeing, Drafts]" in {
      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value

      val dbCreationData = fillDBTest6(viewerAddressRow, viewerUserRow, Drafts)

      val expectedChatsPreview = dbCreationData.emailsPreview.flatten.map(_._2)
        .sortBy(chatPreview =>
          (chatPreview.lastEmailDate, chatPreview.contentPreview, chatPreview.lastAddress))(
          Ordering.Tuple3(Ordering.String.reverse, Ordering.String, Ordering.String))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= dbCreationData.addressRows,
          ChatsTable.all ++= dbCreationData.chatRows,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all ++= dbCreationData.userChatRows,
          EmailsTable.all ++= dbCreationData.emailRows,
          EmailAddressesTable.all ++= dbCreationData.emailAddressRows))

        chatsPreview <- chatsRep.getChatsPreview(Drafts, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview

    }

    "be valid in [Test-6-D: Many Chats, Many Emails, NOT Overseeing, Trash]" in {
      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value

      val dbCreationData = fillDBTest6(viewerAddressRow, viewerUserRow, Trash)

      val expectedChatsPreview = dbCreationData.emailsPreview.flatten.map(_._2)
        .sortBy(chatPreview =>
          (chatPreview.lastEmailDate, chatPreview.contentPreview, chatPreview.lastAddress))(
          Ordering.Tuple3(Ordering.String.reverse, Ordering.String, Ordering.String))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= dbCreationData.addressRows,
          ChatsTable.all ++= dbCreationData.chatRows,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all ++= dbCreationData.userChatRows,
          EmailsTable.all ++= dbCreationData.emailRows,
          EmailAddressesTable.all ++= dbCreationData.emailAddressRows))

        chatsPreview <- chatsRep.getChatsPreview(Trash, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview

    }
    //endregion

    "be valid in [Test-7: 1 Chat, 1 Email, Overseeing, Inbox]" in {

      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val emailRow = genEmailRow(chatRow.chatId).sample.value
      val sent = emailRow.sent
      val trash = genBoolean.sample.value

      val baseParticipantsAddressRows = genParticipantsAddressRows.sample.value
      val viewerParticipantType = genParticipantType.sample.value

      val (participantsAddressRows, optoversightInfo) = viewerParticipantType match {
        case Some(From) => (baseParticipantsAddressRows.copy(from = viewerAddressRow), None)
        case Some(To) => (baseParticipantsAddressRows.copy(to =
          viewerAddressRow +: baseParticipantsAddressRows.to), None)
        case Some(Cc) => (baseParticipantsAddressRows.copy(cc =
          viewerAddressRow +: baseParticipantsAddressRows.cc), None)
        case Some(Bcc) => (baseParticipantsAddressRows.copy(bcc =
          viewerAddressRow +: baseParticipantsAddressRows.bcc), None)
        case Some(Overseer) => addOversee(chatRow.chatId, viewerUserRow.userId, baseParticipantsAddressRows)
        case _ => (baseParticipantsAddressRows, None)
      }

      val emailAddressRows = participantsToEmailAddressRows(emailRow.emailId, chatRow.chatId, participantsAddressRows)
      val addressRows = participantsToAddressRows(participantsAddressRows)

      val baseUserChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
      val userChatRow = (viewerParticipantType, sent, trash) match {
        case (_, _, true) => baseUserChatRow.copy(trash = 1)
        case (Some(From), 1, _) => baseUserChatRow.copy(sent = 1)
        case (Some(From), 0, _) => baseUserChatRow.copy(draft = 1)
        case (Some(_), 1, _) => baseUserChatRow.copy(inbox = 1)
        case _ => baseUserChatRow
      }
      val fromAddress = participantsAddressRows.from.address

      val expectedChatsPreview = if (participantIsReceiving(viewerParticipantType) && sent == 1 && !trash)
        Seq(ChatPreview(chatRow.chatId, chatRow.subject, fromAddress,
          emailRow.date, emailRow.body))
      else Seq.empty[ChatPreview]

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= addressRows,
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += userChatRow,
          EmailsTable.all += emailRow,
          EmailAddressesTable.all ++= emailAddressRows) andThen
          (optoversightInfo match {
            case Some(oversightInfo) => DBIO.seq(
              UsersTable.all += oversightInfo.overseeUserRow,
              OversightsTable.all += oversightInfo.oversightRow)
            case None => DBIO.successful(())
          }))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview

    }

    "be valid in [Test-8-A: Many Chats, Many Emails, Overseeing, Inbox]" in {
      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value

      val dbCreationData = filDB(viewerAddressRow, viewerUserRow, Inbox)

      val expectedChatsPreview = dbCreationData.emailsPreview.flatten.map(_._2)
        .sortBy(chatPreview =>
          (chatPreview.lastEmailDate, chatPreview.contentPreview, chatPreview.lastAddress))(
          Ordering.Tuple3(Ordering.String.reverse, Ordering.String, Ordering.String))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= dbCreationData.addressRows,
          ChatsTable.all ++= dbCreationData.chatRows,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all ++= dbCreationData.userChatRows,
          EmailsTable.all ++= dbCreationData.emailRows,
          EmailAddressesTable.all ++= dbCreationData.emailAddressRows) andThen
          (dbCreationData.oversightInfoList.flatten match {
            case Nil => DBIO.successful(())
            case list => DBIO.seq(
              UsersTable.all ++= list.map(_.overseeUserRow),
              OversightsTable.all ++= list.map(_.oversightRow))
          }))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview

    }

    "be valid in [Test-8-B: Many Chats, Many Emails, Overseeing, Sent]" in {
      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value

      val dbCreationData = filDB(viewerAddressRow, viewerUserRow, Sent)

      val expectedChatsPreview = dbCreationData.emailsPreview.flatten.map(_._2)
        .sortBy(chatPreview =>
          (chatPreview.lastEmailDate, chatPreview.contentPreview, chatPreview.lastAddress))(
          Ordering.Tuple3(Ordering.String.reverse, Ordering.String, Ordering.String))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= dbCreationData.addressRows,
          ChatsTable.all ++= dbCreationData.chatRows,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all ++= dbCreationData.userChatRows,
          EmailsTable.all ++= dbCreationData.emailRows,
          EmailAddressesTable.all ++= dbCreationData.emailAddressRows) andThen
          (dbCreationData.oversightInfoList.flatten match {
            case Nil => DBIO.successful(())
            case list => DBIO.seq(
              UsersTable.all ++= list.map(_.overseeUserRow),
              OversightsTable.all ++= list.map(_.oversightRow))
          }))

        chatsPreview <- chatsRep.getChatsPreview(Sent, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview

    }

    "be valid in [Test-8-C: Many Chats, Many Emails, Overseeing, Drafts]" in {
      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value

      val dbCreationData = filDB(viewerAddressRow, viewerUserRow, Drafts)

      val expectedChatsPreview = dbCreationData.emailsPreview.flatten.map(_._2)
        .sortBy(chatPreview =>
          (chatPreview.lastEmailDate, chatPreview.contentPreview, chatPreview.lastAddress))(
          Ordering.Tuple3(Ordering.String.reverse, Ordering.String, Ordering.String))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= dbCreationData.addressRows,
          ChatsTable.all ++= dbCreationData.chatRows,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all ++= dbCreationData.userChatRows,
          EmailsTable.all ++= dbCreationData.emailRows,
          EmailAddressesTable.all ++= dbCreationData.emailAddressRows) andThen
          (dbCreationData.oversightInfoList.flatten match {
            case Nil => DBIO.successful(())
            case list => DBIO.seq(
              UsersTable.all ++= list.map(_.overseeUserRow),
              OversightsTable.all ++= list.map(_.oversightRow))
          }))

        chatsPreview <- chatsRep.getChatsPreview(Drafts, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview

    }

    "be valid in [Test-8-D: Many Chats, Many Emails, Overseeing, Trash]" in {
      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value

      val dbCreationData = filDB(viewerAddressRow, viewerUserRow, Trash)

      val expectedChatsPreview = dbCreationData.emailsPreview.flatten.map(_._2)
        .sortBy(chatPreview =>
          (chatPreview.lastEmailDate, chatPreview.contentPreview, chatPreview.lastAddress))(
          Ordering.Tuple3(Ordering.String.reverse, Ordering.String, Ordering.String))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= dbCreationData.addressRows,
          ChatsTable.all ++= dbCreationData.chatRows,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all ++= dbCreationData.userChatRows,
          EmailsTable.all ++= dbCreationData.emailRows,
          EmailAddressesTable.all ++= dbCreationData.emailAddressRows) andThen
          (dbCreationData.oversightInfoList.flatten match {
            case Nil => DBIO.successful(())
            case list => DBIO.seq(
              UsersTable.all ++= list.map(_.overseeUserRow),
              OversightsTable.all ++= list.map(_.oversightRow))
          }))

        chatsPreview <- chatsRep.getChatsPreview(Trash, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview

    }

  }*/

  "SlickChatsRepository#getChatsPreview#NEW" should {
    "detect a draft made by the viewer " in {
      val basicTestDB = genBasicTestDB.sample.value

      val expectedChatsPreview = Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.viewerAddressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all += basicTestDB.viewerAddressRow,
          ChatsTable.all += basicTestDB.chatRow,
          UsersTable.all += basicTestDB.viewerUserRow,
          UserChatsTable.all += basicTestDB.userChatRow.copy(draft = 1),
          EmailsTable.all += basicTestDB.emailRow.copy(sent = 0),
          EmailAddressesTable.all += basicTestDB.emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Drafts, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "detect an email sent to the viewer [To]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val senderAddressRow = genAddressRow.sample.value
      val senderEmailAddressesRow = genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
        senderAddressRow.addressId, "from").sample.value

      val expectedChatsPreview = Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        senderAddressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= List(basicTestDB.viewerAddressRow, senderAddressRow),
          ChatsTable.all += basicTestDB.chatRow,
          UsersTable.all += basicTestDB.viewerUserRow,
          UserChatsTable.all += basicTestDB.userChatRow.copy(inbox = 1),
          EmailsTable.all += basicTestDB.emailRow.copy(sent = 1),
          EmailAddressesTable.all ++= List(
            basicTestDB.emailAddressesRow.copy(participantType = "to"),
            senderEmailAddressesRow)))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview

    }

    "detect an email sent to the viewer [Cc]" in {
      val viewerAddressRow = genAddressRow.sample.value
      val senderAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val emailRow = genEmailRow(chatRow.chatId).sample.value.copy(sent = 1)
      val viewerEmailAddressesRow = genEmailAddressRow(emailRow.emailId, chatRow.chatId,
        viewerAddressRow.addressId, "cc").sample.value
      val senderEmailAddressesRow = genEmailAddressRow(emailRow.emailId, chatRow.chatId,
        senderAddressRow.addressId, "from").sample.value
      val userChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
        .copy(inbox = 1)

      val expectedChatsPreview = Seq(ChatPreview(chatRow.chatId, chatRow.subject, senderAddressRow.address,
        emailRow.date, emailRow.body))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= Seq(viewerAddressRow, senderAddressRow),
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += userChatRow,
          EmailsTable.all += emailRow,
          EmailAddressesTable.all ++= Seq(viewerEmailAddressesRow, senderEmailAddressesRow)))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "detect an email sent to the viewer [Bcc]" in {
      val viewerAddressRow = genAddressRow.sample.value
      val senderAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val emailRow = genEmailRow(chatRow.chatId).sample.value.copy(sent = 1)
      val viewerEmailAddressesRow = genEmailAddressRow(emailRow.emailId, chatRow.chatId,
        viewerAddressRow.addressId, "bcc").sample.value
      val senderEmailAddressesRow = genEmailAddressRow(emailRow.emailId, chatRow.chatId,
        senderAddressRow.addressId, "from").sample.value
      val userChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
        .copy(inbox = 1)

      val expectedChatsPreview = Seq(ChatPreview(chatRow.chatId, chatRow.subject, senderAddressRow.address,
        emailRow.date, emailRow.body))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= Seq(viewerAddressRow, senderAddressRow),
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += userChatRow,
          EmailsTable.all += emailRow,
          EmailAddressesTable.all ++= Seq(viewerEmailAddressesRow, senderEmailAddressesRow)))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "Not detect an email addressed to the viewer, that has not been sent [To]" in {
      val viewerAddressRow = genAddressRow.sample.value
      val senderAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val emailRow = genEmailRow(chatRow.chatId).sample.value.copy(sent = 0)
      val viewerEmailAddressesRow = genEmailAddressRow(emailRow.emailId, chatRow.chatId,
        viewerAddressRow.addressId, "to").sample.value
      val senderEmailAddressesRow = genEmailAddressRow(emailRow.emailId, chatRow.chatId,
        senderAddressRow.addressId, "from").sample.value
      val userChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
        .copy(inbox = 1)

      val expectedChatsPreview = Seq.empty[ChatPreview]

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= Seq(viewerAddressRow, senderAddressRow),
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += userChatRow,
          EmailsTable.all += emailRow,
          EmailAddressesTable.all ++= Seq(viewerEmailAddressesRow, senderEmailAddressesRow)))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "Not detect an email addressed to the viewer, that has not been sent [Cc]" in {
      val viewerAddressRow = genAddressRow.sample.value
      val senderAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val emailRow = genEmailRow(chatRow.chatId).sample.value.copy(sent = 0)
      val viewerEmailAddressesRow = genEmailAddressRow(emailRow.emailId, chatRow.chatId,
        viewerAddressRow.addressId, "cc").sample.value
      val senderEmailAddressesRow = genEmailAddressRow(emailRow.emailId, chatRow.chatId,
        senderAddressRow.addressId, "from").sample.value
      val userChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
        .copy(inbox = 1)

      val expectedChatsPreview = Seq.empty[ChatPreview]

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= Seq(viewerAddressRow, senderAddressRow),
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += userChatRow,
          EmailsTable.all += emailRow,
          EmailAddressesTable.all ++= Seq(viewerEmailAddressesRow, senderEmailAddressesRow)))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "Not detect an email addressed to the viewer, that has not been sent [Bcc]" in {
      val viewerAddressRow = genAddressRow.sample.value
      val senderAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val emailRow = genEmailRow(chatRow.chatId).sample.value.copy(sent = 0)
      val viewerEmailAddressesRow = genEmailAddressRow(emailRow.emailId, chatRow.chatId,
        viewerAddressRow.addressId, "bcc").sample.value
      val senderEmailAddressesRow = genEmailAddressRow(emailRow.emailId, chatRow.chatId,
        senderAddressRow.addressId, "from").sample.value
      val userChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
        .copy(inbox = 1)

      val expectedChatsPreview = Seq.empty[ChatPreview]

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= Seq(viewerAddressRow, senderAddressRow),
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += userChatRow,
          EmailsTable.all += emailRow,
          EmailAddressesTable.all ++= Seq(viewerEmailAddressesRow, senderEmailAddressesRow)))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "detect a chat if it is visible in the mailbox being used [Inbox]" in {
      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val emailRow = genEmailRow(chatRow.chatId).sample.value.copy(sent = 0)
      val emailAddressesRow = genEmailAddressRow(emailRow.emailId, chatRow.chatId,
        viewerAddressRow.addressId, "from").sample.value
      val userChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
        .copy(inbox = 1)

      val expectedChatsPreview = Seq(ChatPreview(chatRow.chatId, chatRow.subject, viewerAddressRow.address,
        emailRow.date, emailRow.body))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all += viewerAddressRow,
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += userChatRow,
          EmailsTable.all += emailRow,
          EmailAddressesTable.all += emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "detect a chat if it is visible in the mailbox being used [Sent]" in {
      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val emailRow = genEmailRow(chatRow.chatId).sample.value.copy(sent = 0)
      val emailAddressesRow = genEmailAddressRow(emailRow.emailId, chatRow.chatId,
        viewerAddressRow.addressId, "from").sample.value
      val userChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
        .copy(sent = 1)

      val expectedChatsPreview = Seq(ChatPreview(chatRow.chatId, chatRow.subject, viewerAddressRow.address,
        emailRow.date, emailRow.body))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all += viewerAddressRow,
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += userChatRow,
          EmailsTable.all += emailRow,
          EmailAddressesTable.all += emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Sent, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "detect a chat if it is visible in the mailbox being used [Drafts]" in {
      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val emailRow = genEmailRow(chatRow.chatId).sample.value.copy(sent = 0)
      val emailAddressesRow = genEmailAddressRow(emailRow.emailId, chatRow.chatId,
        viewerAddressRow.addressId, "from").sample.value
      val userChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
        .copy(draft = 1)

      val expectedChatsPreview = Seq(ChatPreview(chatRow.chatId, chatRow.subject, viewerAddressRow.address,
        emailRow.date, emailRow.body))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all += viewerAddressRow,
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += userChatRow,
          EmailsTable.all += emailRow,
          EmailAddressesTable.all += emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Drafts, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "detect a chat if it is visible in the mailbox being used [Trash]" in {
      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val emailRow = genEmailRow(chatRow.chatId).sample.value.copy(sent = 0)
      val emailAddressesRow = genEmailAddressRow(emailRow.emailId, chatRow.chatId,
        viewerAddressRow.addressId, "from").sample.value
      val userChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
        .copy(trash = 1)

      val expectedChatsPreview = Seq(ChatPreview(chatRow.chatId, chatRow.subject, viewerAddressRow.address,
        emailRow.date, emailRow.body))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all += viewerAddressRow,
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += userChatRow,
          EmailsTable.all += emailRow,
          EmailAddressesTable.all += emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Trash, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "Not detect a chat if it isn't visible in the mailbox being used [Inbox]" in {
      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val emailRow = genEmailRow(chatRow.chatId).sample.value.copy(sent = 0)
      val emailAddressesRow = genEmailAddressRow(emailRow.emailId, chatRow.chatId,
        viewerAddressRow.addressId, "from").sample.value
      val userChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value

      val expectedChatsPreview = Seq.empty[ChatPreview]

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all += viewerAddressRow,
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += userChatRow,
          EmailsTable.all += emailRow,
          EmailAddressesTable.all += emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "Not detect a chat if it isn't visible in the mailbox being used [Sent]" in {
      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val emailRow = genEmailRow(chatRow.chatId).sample.value.copy(sent = 0)
      val emailAddressesRow = genEmailAddressRow(emailRow.emailId, chatRow.chatId,
        viewerAddressRow.addressId, "from").sample.value
      val userChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value

      val expectedChatsPreview = Seq.empty[ChatPreview]

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all += viewerAddressRow,
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += userChatRow,
          EmailsTable.all += emailRow,
          EmailAddressesTable.all += emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Sent, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "Not detect a chat if it isn't visible in the mailbox being used [Drafts]" in {
      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val emailRow = genEmailRow(chatRow.chatId).sample.value.copy(sent = 0)
      val emailAddressesRow = genEmailAddressRow(emailRow.emailId, chatRow.chatId,
        viewerAddressRow.addressId, "from").sample.value
      val userChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value

      val expectedChatsPreview = Seq.empty[ChatPreview]

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all += viewerAddressRow,
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += userChatRow,
          EmailsTable.all += emailRow,
          EmailAddressesTable.all += emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Drafts, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "Not detect a chat if it isn't visible in the mailbox being used [Trash]" in {
      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val emailRow = genEmailRow(chatRow.chatId).sample.value.copy(sent = 0)
      val emailAddressesRow = genEmailAddressRow(emailRow.emailId, chatRow.chatId,
        viewerAddressRow.addressId, "from").sample.value
      val userChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value

      val expectedChatsPreview = Seq.empty[ChatPreview]

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all += viewerAddressRow,
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += userChatRow,
          EmailsTable.all += emailRow,
          EmailAddressesTable.all += emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Trash, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "show only the most recent email" in {
      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val recentEmailRow = genEmailRow(chatRow.chatId).sample.value.copy(date = "2019")
      val oldEmailRow = genEmailRow(chatRow.chatId).sample.value.copy(date = "2018")
      val emailAddressesRows = List(genEmailAddressRow(recentEmailRow.emailId, chatRow.chatId,
        viewerAddressRow.addressId, "from").sample.value, genEmailAddressRow(oldEmailRow.emailId, chatRow.chatId,
        viewerAddressRow.addressId, "from").sample.value)
      val userChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
        .copy(inbox = 1)

      val expectedChatsPreview = Seq(ChatPreview(chatRow.chatId, chatRow.subject, viewerAddressRow.address,
        recentEmailRow.date, recentEmailRow.body))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all += viewerAddressRow,
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += userChatRow,
          EmailsTable.all ++= List(recentEmailRow, oldEmailRow),
          EmailAddressesTable.all ++= emailAddressesRows))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "show only the email with the lowest Id in case the dates are repeated" in {
      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val emailRowOne = genEmailRow(chatRow.chatId).sample.value.copy(date = "2019")
      val emailRowTwo = genEmailRow(chatRow.chatId).sample.value.copy(date = "2019")
      val emailAddressesRows = List(genEmailAddressRow(emailRowOne.emailId, chatRow.chatId,
        viewerAddressRow.addressId, "from").sample.value, genEmailAddressRow(emailRowTwo.emailId, chatRow.chatId,
        viewerAddressRow.addressId, "from").sample.value)
      val userChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
        .copy(inbox = 1)

      val predictedEmail = List(emailRowOne, emailRowTwo).minBy(_.emailId)

      val expectedChatsPreview = Seq(ChatPreview(chatRow.chatId, chatRow.subject, viewerAddressRow.address,
        predictedEmail.date, predictedEmail.body))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all += viewerAddressRow,
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += userChatRow,
          EmailsTable.all ++= List(emailRowOne, emailRowTwo),
          EmailAddressesTable.all ++= emailAddressesRows))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "detect multiple chats" in {
      val viewerAddressRow = genAddressRow.sample.value
      val viewerUserRow = genUserRow(viewerAddressRow.addressId).sample.value
      val chatRow = genChatRow.sample.value
      val recentEmailRow = genEmailRow(chatRow.chatId).sample.value.copy(date = "2019")
      val oldEmailRow = genEmailRow(chatRow.chatId).sample.value.copy(date = "2018")
      val emailAddressesRows = List(genEmailAddressRow(recentEmailRow.emailId, chatRow.chatId,
        viewerAddressRow.addressId, "from").sample.value, genEmailAddressRow(oldEmailRow.emailId, chatRow.chatId,
        viewerAddressRow.addressId, "from").sample.value)
      val userChatRow = genUserChatRow(viewerUserRow.userId, chatRow.chatId).sample.value
        .copy(inbox = 1)

      val expectedChatsPreview = Seq(ChatPreview(chatRow.chatId, chatRow.subject, viewerAddressRow.address,
        recentEmailRow.date, recentEmailRow.body))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all += viewerAddressRow,
          ChatsTable.all += chatRow,
          UsersTable.all += viewerUserRow,
          UserChatsTable.all += userChatRow,
          EmailsTable.all ++= List(recentEmailRow, oldEmailRow),
          EmailAddressesTable.all ++= emailAddressesRows))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }
  }

}

case class ParticipantsAddressRows(from: AddressRow, to: List[AddressRow], cc: List[AddressRow], bcc: List[AddressRow])

case class ChatCreationData(userChatRow: UserChatRow, emailRows: List[EmailRow], addressRows: List[AddressRow],
  emailAddressRows: List[EmailAddressRow], emailPreview: EmailPreview)

case class SimpleDBCreationData(chatRows: List[ChatRow], userChatRows: List[UserChatRow], emailRows: List[EmailRow],
  addressRows: List[AddressRow], emailAddressRows: List[EmailAddressRow],
  emailsPreview: List[EmailPreview])

case class DBCreationData(chatRows: List[ChatRow], userChatRows: List[UserChatRow], emailRows: List[EmailRow],
  addressRows: List[AddressRow], emailAddressRows: List[EmailAddressRow],
  emailsPreview: List[EmailPreview], oversightInfoList: List[Option[OversightInfo]])

case class OversightInfo(overseeAddressRow: AddressRow, overseeUserRow: UserRow, oversightRow: OversightRow)

case class BasicTestDB(viewerAddressRow: AddressRow, viewerUserRow: UserRow, chatRow: ChatRow, emailRow: EmailRow,
  emailAddressesRow: EmailAddressRow, userChatRow: UserChatRow)

