package repositories.slick.implementations

import model.types.Mailbox._
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

  def fillDB(addressRows: List[AddressRow], chatRows: List[ChatRow], userRows: List[UserRow],
    userChatRows: List[UserChatRow], emailRows: List[EmailRow], emailAddressRows: List[EmailAddressRow],
    oversightRows: List[OversightRow] = Nil): Future[Unit] =
    db.run(DBIO.seq(
      AddressesTable.all ++= addressRows,
      ChatsTable.all ++= chatRows,
      UsersTable.all ++= userRows,
      UserChatsTable.all ++= userChatRows,
      EmailsTable.all ++= emailRows,
      EmailAddressesTable.all ++= emailAddressRows,
      OversightsTable.all ++= oversightRows))

  "SlickChatsRepository#getChatsPreview" should {
    "detect a draft made by the viewer " in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow),
          List(basicTestDB.userChatRow.copy(draft = 1)),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(basicTestDB.emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Drafts, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.viewerAddressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body))
    }

    "detect an email sent to the viewer [To]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val senderAddressRow = genAddressRow.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow),
          List(basicTestDB.userChatRow.copy(inbox = 1)),
          List(basicTestDB.emailRow.copy(sent = 1)),
          List(
            basicTestDB.emailAddressesRow.copy(participantType = "to"),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        senderAddressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body))

    }

    "detect an email sent to the viewer [CC]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val senderAddressRow = genAddressRow.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow),
          List(basicTestDB.userChatRow.copy(inbox = 1)),
          List(basicTestDB.emailRow.copy(sent = 1)),
          List(
            basicTestDB.emailAddressesRow.copy(participantType = "cc"),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        senderAddressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body))
    }

    "detect an email sent to the viewer [BCC]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val senderAddressRow = genAddressRow.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow),
          List(basicTestDB.userChatRow.copy(inbox = 1)),
          List(basicTestDB.emailRow.copy(sent = 1)),
          List(
            basicTestDB.emailAddressesRow.copy(participantType = "bcc"),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        senderAddressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body))
    }

    "Not detect an email addressed to the viewer, that has not been sent [To]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val senderAddressRow = genAddressRow.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow),
          List(basicTestDB.userChatRow.copy(inbox = 1)),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(
            basicTestDB.emailAddressesRow.copy(participantType = "to"),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe empty
    }

    "Not detect an email addressed to the viewer, that has not been sent [CC]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val senderAddressRow = genAddressRow.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow),
          List(basicTestDB.userChatRow.copy(inbox = 1)),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(
            basicTestDB.emailAddressesRow.copy(participantType = "cc"),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe empty
    }

    "Not detect an email addressed to the viewer, that has not been sent [BCC]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val senderAddressRow = genAddressRow.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow),
          List(basicTestDB.userChatRow.copy(inbox = 1)),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(
            basicTestDB.emailAddressesRow.copy(participantType = "bcc"),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe empty
    }

    "detect a chat if it is visible in the mailbox being used [Inbox]" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow),
          List(basicTestDB.userChatRow.copy(inbox = 1)),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(basicTestDB.emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.viewerAddressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body))
    }

    "detect a chat if it is visible in the mailbox being used [Sent]" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow),
          List(basicTestDB.userChatRow.copy(sent = 1)),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(basicTestDB.emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Sent, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.viewerAddressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body))
    }

    "detect a chat if it is visible in the mailbox being used [Drafts]" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow),
          List(basicTestDB.userChatRow.copy(draft = 1)),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(basicTestDB.emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Drafts, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.viewerAddressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body))
    }

    "detect a chat if it is visible in the mailbox being used [Trash]" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow),
          List(basicTestDB.userChatRow.copy(trash = 1)),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(basicTestDB.emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Trash, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.viewerAddressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body))
    }

    "Not detect a chat if it isn't visible in the mailbox being used [Inbox]" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(basicTestDB.emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe empty
    }

    "Not detect a chat if it isn't visible in the mailbox being used [Sent]" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(basicTestDB.emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Sent, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe empty
    }

    "Not detect a chat if it isn't visible in the mailbox being used [Drafts]" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(basicTestDB.emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Drafts, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe empty
    }

    "Not detect a chat if it isn't visible in the mailbox being used [Trash]" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(basicTestDB.emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Trash, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe empty
    }

    "show only the most recent email" in {
      val basicTestDB = genBasicTestDB.sample.value
      val oldEmailRow = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(date = "2018")

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow),
          List(basicTestDB.userChatRow.copy(inbox = 1)),
          List(basicTestDB.emailRow.copy(date = "2019"), oldEmailRow),
          List(
            basicTestDB.emailAddressesRow,
            genEmailAddressRow(oldEmailRow.emailId, basicTestDB.chatRow.chatId,
              basicTestDB.viewerAddressRow.addressId, "from").sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.viewerAddressRow.address, "2019", basicTestDB.emailRow.body))
    }

    "show only one email even if there are multiple emails with the latest date." in {
      val basicTestDB = genBasicTestDB.sample.value
      val otherEmailRow = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(date = "2019")

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow),
          List(basicTestDB.userChatRow.copy(inbox = 1)),
          List(basicTestDB.emailRow.copy(date = "2019"), otherEmailRow),
          List(
            basicTestDB.emailAddressesRow,
            genEmailAddressRow(otherEmailRow.emailId, basicTestDB.chatRow.chatId,
              basicTestDB.viewerAddressRow.addressId, "from").sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.viewerAddressRow.address, "2019", List(basicTestDB.emailRow, otherEmailRow).minBy(_.emailId).body))

    }

    "detect more than one chat" in {
      val basicTestDB = genBasicTestDB.sample.value
      val otherChatRow = genChatRow.sample.value
      val otherEmailRow = genEmailRow(otherChatRow.chatId).sample.value
      val otherEmailAddressesRow = genEmailAddressRow(otherEmailRow.emailId, otherChatRow.chatId,
        basicTestDB.viewerAddressRow.addressId, "from").sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow),
          List(basicTestDB.chatRow, otherChatRow),
          List(basicTestDB.viewerUserRow),
          List(basicTestDB.userChatRow.copy(inbox = 1), genUserChatRow(
            basicTestDB.viewerUserRow.userId,
            otherChatRow.chatId).sample.value.copy(inbox = 1)),
          List(basicTestDB.emailRow, otherEmailRow),
          List(
            basicTestDB.emailAddressesRow,
            genEmailAddressRow(otherEmailRow.emailId, otherChatRow.chatId,
              basicTestDB.viewerAddressRow.addressId, "from").sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe List(
        ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
          basicTestDB.viewerAddressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body),
        ChatPreview(otherChatRow.chatId, otherChatRow.subject,
          basicTestDB.viewerAddressRow.address, otherEmailRow.date, otherEmailRow.body))
        .sortBy(chatPreview =>
          (chatPreview.lastEmailDate, chatPreview.contentPreview, chatPreview.lastAddress))(
          Ordering.Tuple3(Ordering.String.reverse, Ordering.String, Ordering.String))

    }

    "detect an email made by the oversee if it was sent" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseeAddressRow = genAddressRow.sample.value
      val overseeUserRow = genUserRow(overseeAddressRow.addressId).sample.value
      val overseeEmailRow = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(sent = 1)

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow, overseeAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow, overseeUserRow),
          List(basicTestDB.userChatRow.copy(inbox = 1)),
          List(overseeEmailRow),
          List(genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
            overseeAddressRow.addressId, "from").sample.value),
          List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId,
            overseeUserRow.userId).sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        overseeAddressRow.address, overseeEmailRow.date, overseeEmailRow.body))

    }

    "Not detect an email made by the oversee if it wasn't sent" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseeAddressRow = genAddressRow.sample.value
      val overseeUserRow = genUserRow(overseeAddressRow.addressId).sample.value
      val overseeEmailRow = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(sent = 0)

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow, overseeAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow, overseeUserRow),
          List(basicTestDB.userChatRow.copy(inbox = 1)),
          List(overseeEmailRow),
          List(genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
            overseeAddressRow.addressId, "from").sample.value),
          List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId,
            overseeUserRow.userId).sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe empty
    }

    "detect an email sent to an oversee [To]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseeAddressRow = genAddressRow.sample.value
      val overseeUserRow = genUserRow(overseeAddressRow.addressId).sample.value
      val overseeEmailRow = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(sent = 1)
      val senderAddressRow = genAddressRow.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow, overseeAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow, overseeUserRow),
          List(basicTestDB.userChatRow.copy(inbox = 1)),
          List(overseeEmailRow),
          List(
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              overseeAddressRow.addressId, "to").sample.value,
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value),
          List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId,
            overseeUserRow.userId).sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        senderAddressRow.address, overseeEmailRow.date, overseeEmailRow.body))

    }

    "detect an email sent to an oversee [CC]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseeAddressRow = genAddressRow.sample.value
      val overseeUserRow = genUserRow(overseeAddressRow.addressId).sample.value
      val overseeEmailRow = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(sent = 1)
      val senderAddressRow = genAddressRow.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow, overseeAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow, overseeUserRow),
          List(basicTestDB.userChatRow.copy(inbox = 1)),
          List(overseeEmailRow),
          List(
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              overseeAddressRow.addressId, "cc").sample.value,
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value),
          List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId,
            overseeUserRow.userId).sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        senderAddressRow.address, overseeEmailRow.date, overseeEmailRow.body))
    }

    "detect an email sent to an oversee [BCC]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseeAddressRow = genAddressRow.sample.value
      val overseeUserRow = genUserRow(overseeAddressRow.addressId).sample.value
      val overseeEmailRow = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(sent = 1)
      val senderAddressRow = genAddressRow.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow, overseeAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow, overseeUserRow),
          List(basicTestDB.userChatRow.copy(inbox = 1)),
          List(overseeEmailRow),
          List(
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              overseeAddressRow.addressId, "bcc").sample.value,
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value),
          List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId,
            overseeUserRow.userId).sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        senderAddressRow.address, overseeEmailRow.date, overseeEmailRow.body))

    }

    "Not detect an email addressed to an oversee but not sent [To]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseeAddressRow = genAddressRow.sample.value
      val overseeUserRow = genUserRow(overseeAddressRow.addressId).sample.value
      val overseeEmailRow = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(sent = 0)
      val senderAddressRow = genAddressRow.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow, overseeAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow, overseeUserRow),
          List(basicTestDB.userChatRow.copy(inbox = 1)),
          List(overseeEmailRow),
          List(
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              overseeAddressRow.addressId, "to").sample.value,
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value),
          List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId,
            overseeUserRow.userId).sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe empty
    }

    "Not detect an email addressed to an oversee but not sent [CC]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseeAddressRow = genAddressRow.sample.value
      val overseeUserRow = genUserRow(overseeAddressRow.addressId).sample.value
      val overseeEmailRow = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(sent = 0)
      val senderAddressRow = genAddressRow.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow, overseeAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow, overseeUserRow),
          List(basicTestDB.userChatRow.copy(inbox = 1)),
          List(overseeEmailRow),
          List(
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              overseeAddressRow.addressId, "cc").sample.value,
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value),
          List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId,
            overseeUserRow.userId).sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe empty
    }

    "Not detect an email addressed to an oversee but not sent [BCC]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseeAddressRow = genAddressRow.sample.value
      val overseeUserRow = genUserRow(overseeAddressRow.addressId).sample.value
      val overseeEmailRow = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(sent = 0)
      val senderAddressRow = genAddressRow.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow, overseeAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow, overseeUserRow),
          List(basicTestDB.userChatRow.copy(inbox = 1)),
          List(overseeEmailRow),
          List(
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              overseeAddressRow.addressId, "bcc").sample.value,
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value),
          List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId,
            overseeUserRow.userId).sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe empty
    }

  }

}

case class BasicTestDB(viewerAddressRow: AddressRow, viewerUserRow: UserRow, chatRow: ChatRow, emailRow: EmailRow,
  emailAddressesRow: EmailAddressRow, userChatRow: UserChatRow)

