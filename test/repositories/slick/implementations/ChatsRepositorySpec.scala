package repositories.slick.implementations

import model.types.Mailbox._
import org.scalatest._
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import repositories.dtos._
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

  /* "SlickChatsRepository#getChatsPreview" should {
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
          List(basicTestDB.userChatRow),
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
          List(basicTestDB.userChatRow),
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
          List(basicTestDB.userChatRow),
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
          List(basicTestDB.userChatRow),
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
          List(basicTestDB.userChatRow),
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
          List(basicTestDB.userChatRow),
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
          List(basicTestDB.userChatRow),
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
          List(basicTestDB.userChatRow.copy(inbox = 0)),
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
          List(basicTestDB.userChatRow),
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
          List(basicTestDB.userChatRow),
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
          List(basicTestDB.userChatRow, genUserChatRow(
            basicTestDB.viewerUserRow.userId,
            otherChatRow.chatId).sample.value),
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
          List(basicTestDB.userChatRow),
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
          List(basicTestDB.userChatRow),
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
          List(basicTestDB.userChatRow),
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
          List(basicTestDB.userChatRow),
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
          List(basicTestDB.userChatRow),
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
          List(basicTestDB.userChatRow),
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
          List(basicTestDB.userChatRow),
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
          List(basicTestDB.userChatRow),
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

  }*/

  "SlickChatsRepository#getChat" should {
    "Not detect a non existing chat" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(basicTestDB.emailAddressesRow))

        optChat <- chatsRep.getChat(genUUID.sample.value, basicTestDB.viewerUserRow.userId)
      } yield optChat mustBe None
    }

    "Not detect a chat the user does not have access to" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow),
          List(),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(basicTestDB.emailAddressesRow))

        optChat <- chatsRep.getChat(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId)
      } yield optChat mustBe None
    }

    "detect a draft made by the viewer " in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(basicTestDB.emailAddressesRow))

        optChat <- chatsRep.getChat(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId)
      } yield optChat mustBe Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(basicTestDB.viewerAddressRow.address), Set(),
        Seq(Email(basicTestDB.emailRow.emailId, basicTestDB.viewerAddressRow.address, Set(), Set(), Set(),
          basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 0, Set()))))
    }

    "detect only emails addressed to the viewer that were sent [To]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val senderAddressRow = genAddressRow.sample.value
      val notSentEmail = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(sent = 0)

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(sent = 1), notSentEmail),
          List(
            basicTestDB.emailAddressesRow.copy(participantType = "to"),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, "from")
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, basicTestDB.viewerAddressRow.addressId,
              "to").sample.value))

        optChat <- chatsRep.getChat(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId)
      } yield optChat mustBe Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(basicTestDB.viewerAddressRow.address, senderAddressRow.address), Set(),
        Seq(Email(basicTestDB.emailRow.emailId, senderAddressRow.address,
          Set(basicTestDB.viewerAddressRow.address), Set(), Set(),
          basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 1, Set()))))

    }

    "detect only emails addressed to the viewer that were sent [CC]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val senderAddressRow = genAddressRow.sample.value
      val notSentEmail = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(sent = 0)

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(sent = 1), notSentEmail),
          List(
            basicTestDB.emailAddressesRow.copy(participantType = "cc"),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, "from")
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, basicTestDB.viewerAddressRow.addressId,
              "cc").sample.value))

        optChat <- chatsRep.getChat(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId)
      } yield optChat mustBe Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(basicTestDB.viewerAddressRow.address, senderAddressRow.address), Set(),
        Seq(Email(basicTestDB.emailRow.emailId, senderAddressRow.address,
          Set(), Set(), Set(basicTestDB.viewerAddressRow.address),
          basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 1, Set()))))
    }

    "detect only emails addressed to the viewer that were sent [BCC]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val senderAddressRow = genAddressRow.sample.value
      val notSentEmail = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(sent = 0)

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(sent = 1), notSentEmail),
          List(
            basicTestDB.emailAddressesRow.copy(participantType = "bcc"),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, "from")
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, basicTestDB.viewerAddressRow.addressId,
              "bcc").sample.value))

        optChat <- chatsRep.getChat(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId)
      } yield optChat mustBe Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(basicTestDB.viewerAddressRow.address, senderAddressRow.address), Set(),
        Seq(Email(basicTestDB.emailRow.emailId, senderAddressRow.address,
          Set(), Set(basicTestDB.viewerAddressRow.address), Set(),
          basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 1, Set()))))
    }

    "show the emails ordered by date" in {
      val basicTestDB = genBasicTestDB.sample.value
      val oldEmailRow = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(date = "2018")

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(date = "2019"), oldEmailRow),
          List(
            basicTestDB.emailAddressesRow,
            genEmailAddressRow(oldEmailRow.emailId, basicTestDB.chatRow.chatId,
              basicTestDB.viewerAddressRow.addressId, "from").sample.value))

        optChat <- chatsRep.getChat(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId)
      } yield optChat mustBe Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(basicTestDB.viewerAddressRow.address), Set(),
        Seq(
          Email(oldEmailRow.emailId, basicTestDB.viewerAddressRow.address, Set(), Set(), Set(),
            oldEmailRow.body, oldEmailRow.date, oldEmailRow.sent, Set()),
          Email(basicTestDB.emailRow.emailId, basicTestDB.viewerAddressRow.address, Set(), Set(), Set(),
            basicTestDB.emailRow.body, "2019", basicTestDB.emailRow.sent, Set()))))
    }

    "detect only emails made by the oversee if they were sent" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseeAddressRow = genAddressRow.sample.value
      val overseeUserRow = genUserRow(overseeAddressRow.addressId).sample.value
      val sentEmail = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(sent = 1)
      val notSentEmail = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(sent = 0)

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow, overseeAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow, overseeUserRow),
          List(basicTestDB.userChatRow),
          List(sentEmail, notSentEmail),
          List(
            genEmailAddressRow(sentEmail.emailId, basicTestDB.chatRow.chatId, overseeAddressRow.addressId, "from")
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, overseeAddressRow.addressId, "from")
              .sample.value),
          List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId,
            overseeUserRow.userId).sample.value))

        optChat <- chatsRep.getChat(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId)
      } yield optChat mustBe Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(overseeAddressRow.address),
        Set(Overseers(overseeAddressRow.address, Set(basicTestDB.viewerAddressRow.address))),
        Seq(Email(sentEmail.emailId, overseeAddressRow.address, Set(), Set(), Set(),
          sentEmail.body, sentEmail.date, sent = 1, Set()))))

    }

    "detect only emails addressed to the oversee if they were sent [To]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseeAddressRow = genAddressRow.sample.value
      val senderAddressRow = genAddressRow.sample.value
      val overseeUserRow = genUserRow(overseeAddressRow.addressId).sample.value
      val sentEmail = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(sent = 1)
      val notSentEmail = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(sent = 0)

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow, overseeAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow, overseeUserRow),
          List(basicTestDB.userChatRow),
          List(sentEmail, notSentEmail),
          List(
            genEmailAddressRow(sentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, "from")
              .sample.value,
            genEmailAddressRow(sentEmail.emailId, basicTestDB.chatRow.chatId, overseeAddressRow.addressId, "to")
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, "from")
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, overseeAddressRow.addressId, "to")
              .sample.value),
          List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId,
            overseeUserRow.userId).sample.value))

        optChat <- chatsRep.getChat(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId)
      } yield optChat mustBe Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(overseeAddressRow.address, senderAddressRow.address),
        Set(Overseers(overseeAddressRow.address, Set(basicTestDB.viewerAddressRow.address))),
        Seq(Email(sentEmail.emailId, senderAddressRow.address, Set(overseeAddressRow.address), Set(), Set(),
          sentEmail.body, sentEmail.date, sent = 1, Set()))))

    }

    "detect only emails addressed to the oversee if they were sent [CC]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseeAddressRow = genAddressRow.sample.value
      val senderAddressRow = genAddressRow.sample.value
      val overseeUserRow = genUserRow(overseeAddressRow.addressId).sample.value
      val sentEmail = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(sent = 1)
      val notSentEmail = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(sent = 0)

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow, overseeAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow, overseeUserRow),
          List(basicTestDB.userChatRow),
          List(sentEmail, notSentEmail),
          List(
            genEmailAddressRow(sentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, "from")
              .sample.value,
            genEmailAddressRow(sentEmail.emailId, basicTestDB.chatRow.chatId, overseeAddressRow.addressId, "cc")
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, "from")
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, overseeAddressRow.addressId, "cc")
              .sample.value),
          List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId,
            overseeUserRow.userId).sample.value))

        optChat <- chatsRep.getChat(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId)
      } yield optChat mustBe Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(overseeAddressRow.address, senderAddressRow.address),
        Set(Overseers(overseeAddressRow.address, Set(basicTestDB.viewerAddressRow.address))),
        Seq(Email(sentEmail.emailId, senderAddressRow.address, Set(), Set(), Set(overseeAddressRow.address),
          sentEmail.body, sentEmail.date, sent = 1, Set()))))

    }

    "detect only emails addressed to the oversee if they were sent [BCC]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseeAddressRow = genAddressRow.sample.value
      val senderAddressRow = genAddressRow.sample.value
      val overseeUserRow = genUserRow(overseeAddressRow.addressId).sample.value
      val sentEmail = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(sent = 1)
      val notSentEmail = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(sent = 0)

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow, overseeAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow, overseeUserRow),
          List(basicTestDB.userChatRow),
          List(sentEmail, notSentEmail),
          List(
            genEmailAddressRow(sentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, "from")
              .sample.value,
            genEmailAddressRow(sentEmail.emailId, basicTestDB.chatRow.chatId, overseeAddressRow.addressId, "bcc")
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, "from")
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, overseeAddressRow.addressId, "bcc")
              .sample.value),
          List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId,
            overseeUserRow.userId).sample.value))

        optChat <- chatsRep.getChat(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId)
      } yield optChat mustBe Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(overseeAddressRow.address, senderAddressRow.address),
        Set(Overseers(overseeAddressRow.address, Set(basicTestDB.viewerAddressRow.address))),
        Seq(Email(sentEmail.emailId, senderAddressRow.address, Set(), Set(overseeAddressRow.address), Set(),
          sentEmail.body, sentEmail.date, sent = 1, Set()))))

    }

 /*   "show a bcc address to an Overseer only if their Oversee is the sender of the email " +
      "or is the user linked to said bcc address" in {
        val basicTestDB = genBasicTestDB.sample.value
        val toAddressRow = genAddressRow.sample.value
        val ccAddressRow = genAddressRow.sample.value
        val bccAddressRow = genAddressRow.sample.value
      val fromOverseerAddressRow = genAddressRow.sample.value
      val toOverseerAddressRow = genAddressRow.sample.value
      val ccOverseerAddressRow = genAddressRow.sample.value
      val bccOverseerAddressRow = genAddressRow.sample.value
      val toUserRow = genUserRow(toAddressRow.addressId).sample.value
        val ccUserRow = genUserRow(ccAddressRow.addressId).sample.value
        val bccUserRow = genUserRow(bccAddressRow.addressId).sample.value
      val fromOverseerUserRow = genUserRow(fromOverseerAddressRow.addressId).sample.value
      val toOverseerUserRow = genUserRow(toOverseerAddressRow.addressId).sample.value
      val ccOverseerUserRow = genUserRow(ccOverseerAddressRow.addressId).sample.value
      val bccOverseerUserRow = genUserRow(bccOverseerAddressRow.addressId).sample.value

        val visibleBCCOptChat = Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
          Set(basicTestDB.viewerAddressRow.address, toAddressRow.address, ccAddressRow.address, bccAddressRow.address),
          Set(Overseers(basicTestDB.viewerAddressRow.)),
          Seq(Email(basicTestDB.emailRow.emailId, basicTestDB.viewerAddressRow.address,
            Set(toAddressRow.address), Set(bccAddressRow.address), Set(ccAddressRow.address),
            basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 1, Set()))))

        val notVisibleBCCOptChat = Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
          Set(basicTestDB.viewerAddressRow.address, toAddressRow.address, ccAddressRow.address), Set(),
          Seq(Email(basicTestDB.emailRow.emailId, basicTestDB.viewerAddressRow.address,
            Set(toAddressRow.address), Set(), Set(ccAddressRow.address),
            basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 1, Set()))))

        for {
          _ <- fillDB(
            List(basicTestDB.viewerAddressRow, toAddressRow, ccAddressRow, bccAddressRow),
            List(basicTestDB.chatRow),
            List(basicTestDB.viewerUserRow, toUserRow, ccUserRow, bccUserRow),
            List(basicTestDB.userChatRow, genUserChatRow(toUserRow.userId, basicTestDB.chatRow.chatId).sample.value,
              genUserChatRow(ccUserRow.userId, basicTestDB.chatRow.chatId).sample.value,
              genUserChatRow(bccUserRow.userId, basicTestDB.chatRow.chatId).sample.value),
            List(basicTestDB.emailRow.copy(sent = 1)),
            List(
              basicTestDB.emailAddressesRow,
              genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
                toAddressRow.addressId, "to").sample.value,
              genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
                ccAddressRow.addressId, "cc").sample.value,
              genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
                bccAddressRow.addressId, "bcc").sample.value))

          optChatFrom <- chatsRep.getChat(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId)
          optChatTo <- chatsRep.getChat(basicTestDB.chatRow.chatId, toUserRow.userId)
          optChatCC <- chatsRep.getChat(basicTestDB.chatRow.chatId, ccUserRow.userId)
          optChatBCC <- chatsRep.getChat(basicTestDB.chatRow.chatId, bccUserRow.userId)

        } yield assert(
          optChatFrom === visibleBCCOptChat &&
            optChatTo === notVisibleBCCOptChat &&
            optChatCC === notVisibleBCCOptChat &&
            optChatBCC === visibleBCCOptChat)

      }

    "show all bcc addresses to the sender of the email, but each bcc user can only see their own address" in {
      val basicTestDB = genBasicTestDB.sample.value
      val bccOneAddressRow = genAddressRow.sample.value
      val bccTwoAddressRow = genAddressRow.sample.value
      val bccOneUserRow = genUserRow(bccOneAddressRow.addressId).sample.value
      val bccTwoUserRow = genUserRow(bccTwoAddressRow.addressId).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.viewerAddressRow, bccOneAddressRow, bccTwoAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.viewerUserRow, bccOneUserRow, bccTwoUserRow),
          List(basicTestDB.userChatRow, genUserChatRow(bccOneUserRow.userId, basicTestDB.chatRow.chatId).sample.value,
            genUserChatRow(bccTwoUserRow.userId, basicTestDB.chatRow.chatId).sample.value),
          List(basicTestDB.emailRow.copy(sent = 1)),
          List(
            basicTestDB.emailAddressesRow,
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              bccOneAddressRow.addressId, "bcc").sample.value,
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              bccTwoAddressRow.addressId, "bcc").sample.value))

        optChatFrom <- chatsRep.getChat(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId)
        optChatBCCOne <- chatsRep.getChat(basicTestDB.chatRow.chatId, bccOneUserRow.userId)
        optChatBCCTwo <- chatsRep.getChat(basicTestDB.chatRow.chatId, bccTwoUserRow.userId)

      } yield assert(
        optChatFrom === Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
          Set(basicTestDB.viewerAddressRow.address, bccOneAddressRow.address, bccTwoAddressRow.address), Set(),
          Seq(Email(basicTestDB.emailRow.emailId, basicTestDB.viewerAddressRow.address,
            Set(), Set(bccOneAddressRow.address, bccTwoAddressRow.address), Set(),
            basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 1, Set()))))
          &&
          optChatBCCOne === Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
            Set(basicTestDB.viewerAddressRow.address, bccOneAddressRow.address), Set(),
            Seq(Email(basicTestDB.emailRow.emailId, basicTestDB.viewerAddressRow.address,
              Set(), Set(bccOneAddressRow.address), Set(),
              basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 1, Set()))))
          &&
          optChatBCCTwo === Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
            Set(basicTestDB.viewerAddressRow.address, bccTwoAddressRow.address), Set(),
            Seq(Email(basicTestDB.emailRow.emailId, basicTestDB.viewerAddressRow.address,
              Set(), Set(bccTwoAddressRow.address), Set(),
              basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 1, Set())))))

    }*/

  }

}

case class BasicTestDB(viewerAddressRow: AddressRow, viewerUserRow: UserRow, chatRow: ChatRow, emailRow: EmailRow,
  emailAddressesRow: EmailAddressRow, userChatRow: UserChatRow)

