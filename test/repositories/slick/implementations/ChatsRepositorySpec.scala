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

  "SlickChatsRepository#getChatsPreview" should {
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

      println("THIS IS THE VIEWER_ADDRESS_ROW", viewerAddressRow)
      println("THIS IS THE VIEWER_USER_ROW", viewerUserRow)
      println("THIS IS THE PARTICIPANT_TYPE", viewerParticipantType)
      println("THIS IS THE SENT", sent)
      println("THIS IS THE TRASH", trash)

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

      println("THIS IS THE VIEWER_ADDRESS_ROW", viewerAddressRow)
      println("THIS IS THE VIEWER_USER_ROW", viewerUserRow)
      println("THIS IS THE PARTICIPANT_TYPE", viewerParticipantType)
      println("THIS IS THE SENT", sent)
      println("THIS IS THE TRASH", trash)

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

      println("THIS IS THE VIEWER_ADDRESS_ROW", viewerAddressRow)
      println("THIS IS THE VIEWER_USER_ROW", viewerUserRow)
      println("THIS IS THE PARTICIPANT_TYPE", viewerParticipantType)
      println("THIS IS THE SENT", sent)
      println("THIS IS THE TRASH", trash)

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

      println("THIS IS THE VIEWER_ADDRESS_ROW", viewerAddressRow)
      println("THIS IS THE VIEWER_USER_ROW", viewerUserRow)
      println("THIS IS THE PARTICIPANT_TYPE", viewerParticipantType)
      println("THIS IS THE SENT", sent)
      println("THIS IS THE TRASH", trash)

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

      println("THIS IS THE VIEWER_ADDRESS_ROW", viewerAddressRow)
      println("THIS IS THE VIEWER_USER_ROW", viewerUserRow)
      println("THIS IS THE TRASH", trash)
      println("THIS IS THE ADDRESS_ROWS TO BE INSERTED", trashedChatCreationData.addressRows)

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

      println("THIS IS THE VIEWER_ADDRESS_ROW", viewerAddressRow)
      println("THIS IS THE VIEWER_USER_ROW", viewerUserRow)
      println("THIS IS THE TRASH", trash)
      println("THIS IS THE ADDRESS_ROWS TO BE INSERTED", trashedChatCreationData.addressRows)

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

      println("THIS IS THE VIEWER_ADDRESS_ROW", viewerAddressRow)
      println("THIS IS THE VIEWER_USER_ROW", viewerUserRow)
      println("THIS IS THE TRASH", trash)
      println("THIS IS THE ADDRESS_ROWS TO BE INSERTED", trashedChatCreationData.addressRows)

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

      println("THIS IS THE VIEWER_ADDRESS_ROW", viewerAddressRow)
      println("THIS IS THE VIEWER_USER_ROW", viewerUserRow)
      println("THIS IS THE TRASH", trash)
      println("THIS IS THE ADDRESS_ROWS TO BE INSERTED", trashedChatCreationData.addressRows)

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

      println("THIS IS THE VIEWER_ADDRESS_ROW", viewerAddressRow)
      println("THIS IS THE VIEWER_USER_ROW", viewerUserRow)
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

      println("THIS IS THE VIEWER_ADDRESS_ROW", viewerAddressRow)
      println("THIS IS THE VIEWER_USER_ROW", viewerUserRow)
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

      println("THIS IS THE VIEWER_ADDRESS_ROW", viewerAddressRow)
      println("THIS IS THE VIEWER_USER_ROW", viewerUserRow)
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

      println("THIS IS THE VIEWER_ADDRESS_ROW", viewerAddressRow)
      println("THIS IS THE VIEWER_USER_ROW", viewerUserRow)
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

      println("THIS IS THE VIEWER_ADDRESS_ROW", viewerAddressRow)
      println("THIS IS THE VIEWER_USER_ROW", viewerUserRow)
      println("THIS IS THE PARTICIPANT_TYPE", viewerParticipantType)
      println("THIS IS THE SENT", sent)
      println("THIS IS THE TRASH", trash)

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

      println("THIS IS THE VIEWER_ADDRESS_ROW", viewerAddressRow)
      println("THIS IS THE VIEWER_USER_ROW", viewerUserRow)
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

      println("THIS IS THE VIEWER_ADDRESS_ROW", viewerAddressRow)
      println("THIS IS THE VIEWER_USER_ROW", viewerUserRow)
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

      println("THIS IS THE VIEWER_ADDRESS_ROW", viewerAddressRow)
      println("THIS IS THE VIEWER_USER_ROW", viewerUserRow)
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

      println("THIS IS THE VIEWER_ADDRESS_ROW", viewerAddressRow)
      println("THIS IS THE VIEWER_USER_ROW", viewerUserRow)
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
        inserted <- db.run(chatsRep.upsertAddress(address))
        selected <- db.run(AddressesTable.selectAddressId(address).result.head)
      } yield inserted mustBe selected

      //val debugPrint = db.run(AddressesTable.all.result).map(_.map(a => println(a.addressId + "-" + a.address)))
    }

    "return the addressId if the address already exists in the table" in {


      val address = "beatriz@mail.com"
      for {
        inserted <- db.run(chatsRep.upsertAddress(address))
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
        UpsertEmailDTO(
          emailId = None,
          from = Some("beatriz@mail.com"),
          to = Some(Set("joao@mail.com", "notuser@mail.com")),
          bcc = Some(Set("spy@mail.com")),
          cc = Some(Set("observer@mail.com")),
          body = Some("Test Body"),
          date = None,
          sent = None))

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
          UpsertEmailDTO(
            emailId = None,
            from = Some("beatriz@mail.com"),
            to = None,
            bcc = None,
            cc = None,
            body = None,
            date = None,
            sent = None))

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

  /*
  "SlickChatsRepository#patchEmail" should {
    val chatsRep = new SlickChatsRepository(db)
    val userId = "148a3b1b-8326-466d-8c27-1bd09b8378f3" //beatriz@mail.com
    val userAddress = "beatriz@mail.com"

    "patch one or more fields of an email in draft state" in {
      val createChatDTO = CreateChatDTO(None, Some("Test"),
        UpsertEmailDTO(None, None, Some(Set("joao@mail.com")), None, None, Some("This is the email's body"), None, Some(false)))
      for {
        postChat <- chatsRep.postChat(createChatDTO, userId)
        getPostedEmail = chatsRep.fromCreateChatDTOtoChatDTO(postChat).emails.head

        patchBody = "This is me changing the body"
        patchEmailDTO = UpsertEmailDTO(None, None, None, None, None, Some(patchBody), None, None)

        patchEmail <- chatsRep.patchEmail(patchEmailDTO, postChat.chatId.value, postChat.email.emailId.value, userId)
      } yield patchEmail.value mustBe getPostedEmail.copy(body = patchBody, date = patchEmail.value.date)
    }

    "patch the email, send it if the field sent is true and " +
      "the chat must appear in the sender and receivers' correct mailboxes" in {
        val createChatDTO = CreateChatDTO(None, Some("Test"),
          UpsertEmailDTO(None, None, Some(Set("joao@mail.com")), None, None, Some("This is the email's body"), None, Some(false)))
        for {
          postChat <- chatsRep.postChat(createChatDTO, userId)
          getPostedChat = chatsRep.fromCreateChatDTOtoChatDTO(postChat)
          getPostedEmail = getPostedChat.emails.head

          patchCC = Set("valter@mail.com")
          patchBody = "This is me changing the body"
          patchSent = true
          patchEmailDTO = UpsertEmailDTO(None, None, None, None, Some(patchCC), Some(patchBody), None, Some(patchSent))

          patchEmail <- chatsRep.patchEmail(patchEmailDTO, postChat.chatId.value, postChat.email.emailId.value, userId)

          toUserId = "adcd6348-658a-4866-93c5-7e6d32271d8d" //joao@mail.com
          ccUserId = "25689204-5a8e-453d-bfbc-4180ff0f97b9" //valter@mail.com

          toUserGetChat <- chatsRep.getChat(postChat.chatId.value, toUserId)
          ccUserGetChat <- chatsRep.getChat(postChat.chatId.value, ccUserId)

          //Sender UserChat
          senderChatsPreviewSent <- chatsRep.getChatsPreview(Sent, userId)
          senderChatsPreviewDrafts <- chatsRep.getChatsPreview(Drafts, userId)

          //Receivers UserChat
          toReceiverChatsPreviewInbox <- chatsRep.getChatsPreview(Inbox, toUserId)
          ccReceiverChatsPreviewInbox <- chatsRep.getChatsPreview(Inbox, ccUserId)

          expectedEmailAfterPatch = getPostedEmail.copy(cc = patchCC, body = patchBody,
            date = patchEmail.value.date, sent = if (patchSent) 1 else 0)
          expectedChatAfterPatch = getPostedChat.copy(
            addresses = getPostedChat.addresses ++ patchCC,
            emails = Seq(expectedEmailAfterPatch))

          expectedChatPreview = ChatPreview(getPostedChat.chatId, getPostedChat.subject, userAddress, patchEmail.value.date, patchBody)

        } yield assert(
          patchEmail.value === expectedEmailAfterPatch &&
            toUserGetChat.value === expectedChatAfterPatch &&
            ccUserGetChat.value === expectedChatAfterPatch &&

            senderChatsPreviewSent.contains(expectedChatPreview) &&
            !senderChatsPreviewDrafts.contains(expectedChatPreview) &&

            toReceiverChatsPreviewInbox.contains(expectedChatPreview) &&
            ccReceiverChatsPreviewInbox.contains(expectedChatPreview))

      }

    "not send an email if the receivers list (to + bcc + cc) is empty" in {
      val chatWithNoReceiversDTO = CreateChatDTO(None, Some("Test"),
        UpsertEmailDTO(None, None, None, None, None, Some("This is an email with no receivers"), None, Some(false)))
      for {
        postChat <- chatsRep.postChat(chatWithNoReceiversDTO, userId)
        getPostedEmail = chatsRep.fromCreateChatDTOtoChatDTO(postChat).emails.head

        trySendEmailDTO = UpsertEmailDTO(None, None, None, None, None, None, None, Some(true))

        patchEmail <- chatsRep.patchEmail(trySendEmailDTO, postChat.chatId.value, postChat.email.emailId.value, userId)
      } yield assert(
        patchEmail.value === getPostedEmail &&
          patchEmail.value.sent === 0)
    }

    "replace the addresses field (to, bcc or cc) with the values specified in the patch (Some(...))" in {
      val chatWithNoReceiversDTO = CreateChatDTO(None, Some("Test"),
        UpsertEmailDTO(None, None, Some(Set("joao@mail.com", "mariana@mail.com")), None, Some(Set("ivo@mail.com")),
          Some("This is an email with no receivers"), None, Some(false)))
      for {
        postChat <- chatsRep.postChat(chatWithNoReceiversDTO, userId)
        getPostedEmail = chatsRep.fromCreateChatDTOtoChatDTO(postChat).emails.head

        newToAddresses: Set[String] = Set("valter@mail.com", "joao@mail.com", "joana@mail.com")
        newBccAddresses: Set[String] = Set("ivo@mail.com")
        newCcAddresses: Set[String] = Set()

        patchAddressesEmailDTO = UpsertEmailDTO(None, None,
          Some(newToAddresses), Some(newBccAddresses), Some(newCcAddresses), None, None, None)

        patchEmail <- chatsRep.patchEmail(patchAddressesEmailDTO, postChat.chatId.value, postChat.email.emailId.value, userId)
      } yield patchEmail.value mustBe getPostedEmail.copy(to = newToAddresses, bcc = newBccAddresses,
        cc = newCcAddresses, date = patchEmail.value.date)
    }

    "not update a field if it is not specified in the patch (None)" in {
      val chatWithNoReceiversDTO = CreateChatDTO(None, Some("Test"),
        UpsertEmailDTO(None, None, Some(Set("joao@mail.com", "valter@mail.com", "pedroc@mail.com")), None, Some(Set("ivo@mail.com")),
          Some("This is an email with no receivers"), None, Some(false)))
      for {
        postChat <- chatsRep.postChat(chatWithNoReceiversDTO, userId)
        getPostedEmail = chatsRep.fromCreateChatDTOtoChatDTO(postChat).emails.head

        newBccAddresses: Set[String] = Set("rui@mail.com", "pedrol@mail.com")

        patchAddressesEmailDTO = UpsertEmailDTO(None, None,
          None, Some(newBccAddresses), None, None, None, None)

        patchEmail <- chatsRep.patchEmail(patchAddressesEmailDTO, postChat.chatId.value, postChat.email.emailId.value, userId)
      } yield patchEmail.value mustBe getPostedEmail.copy(bcc = newBccAddresses, date = patchEmail.value.date)
    }

    "not allow an email patch if the user requesting it is not the its owner (from)" in {
      val createChatDTO = CreateChatDTO(None, Some("Test"),
        UpsertEmailDTO(None, None, Some(Set("joao@mail.com", "pedroc@mail.com")), None, None, Some("This is the email's body"), None, Some(false)))
      for {
        postChat <- chatsRep.postChat(createChatDTO, userId)

        patchEmailDTO = UpsertEmailDTO(None, None, None, None, None,
          Some("This is an unauthorized user trying to patch the email"), None, None)
        userIdNotAllowedToPatch = "adcd6348-658a-4866-93c5-7e6d32271d8d" //joao@mail.com

        patchEmail <- chatsRep.patchEmail(patchEmailDTO, postChat.chatId.value, postChat.email.emailId.value, userIdNotAllowedToPatch)
      } yield patchEmail mustBe None
    }

    "not allow an email patch if the email was already sent" in {
      val createChatDTO = CreateChatDTO(None, Some("Test"),
        UpsertEmailDTO(None, None, Some(Set("joao@mail.com", "pedroc@mail.com")), None, None, Some("This is the email's body"), None, Some(false)))
      for {
        postChat <- chatsRep.postChat(createChatDTO, userId)
        sent = true
        patchEmailDTO = UpsertEmailDTO(None, None, None, None, None, None, None, Some(sent))
        patchEmail <- chatsRep.patchEmail(patchEmailDTO, postChat.chatId.value, postChat.email.emailId.value, userId)

        retryPatchEmailDTO = UpsertEmailDTO(None, None, None, None, None,
          Some("Trying to change body after being sent"), None, None)
        retryPatchEmailAfterSent <- chatsRep.patchEmail(retryPatchEmailDTO, postChat.chatId.value, postChat.email.emailId.value, userId)
      } yield retryPatchEmailAfterSent mustBe None
    }

    "return None if the requested emailId is not a part of the chat with the specified chatId" in {
      val createChatDTO = CreateChatDTO(None, Some("Test"),
        UpsertEmailDTO(None, None, Some(Set("joao@mail.com", "pedroc@mail.com")), None, None, Some("This is the email's body"), None, Some(false)))
      for {
        postChat <- chatsRep.postChat(createChatDTO, userId)
        createdChatId = postChat.chatId.value
        invalidEmailId = "00000000-0000-0000-0000-000000000000"

        patchEmailDTO = UpsertEmailDTO(None, None, None, None, None,
          Some("This is an unauthorized user trying to patch the email"), None, None)
        patchEmail <- chatsRep.patchEmail(patchEmailDTO, createdChatId, invalidEmailId, userId)

      } yield patchEmail mustBe None
    }

  }

  */
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

