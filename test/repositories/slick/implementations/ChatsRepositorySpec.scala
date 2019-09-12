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
	
	
  "SlickChatsRepository#getChatsPreview" should {
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
            basicTestDB.emailAddressesRow.copy(participantType = "cc"),
            senderEmailAddressesRow)))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "detect an email sent to the viewer [Bcc]" in {
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
            basicTestDB.emailAddressesRow.copy(participantType = "bcc"),
            senderEmailAddressesRow)))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "Not detect an email addressed to the viewer, that has not been sent [To]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val senderAddressRow = genAddressRow.sample.value
      val senderEmailAddressesRow = genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
        senderAddressRow.addressId, "from").sample.value

      val expectedChatsPreview = Seq.empty[ChatPreview]

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= List(basicTestDB.viewerAddressRow, senderAddressRow),
          ChatsTable.all += basicTestDB.chatRow,
          UsersTable.all += basicTestDB.viewerUserRow,
          UserChatsTable.all += basicTestDB.userChatRow.copy(inbox = 1),
          EmailsTable.all += basicTestDB.emailRow.copy(sent = 0),
          EmailAddressesTable.all ++= List(
            basicTestDB.emailAddressesRow.copy(participantType = "to"),
            senderEmailAddressesRow)))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "Not detect an email addressed to the viewer, that has not been sent [Cc]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val senderAddressRow = genAddressRow.sample.value
      val senderEmailAddressesRow = genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
        senderAddressRow.addressId, "from").sample.value

      val expectedChatsPreview = Seq.empty[ChatPreview]

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= List(basicTestDB.viewerAddressRow, senderAddressRow),
          ChatsTable.all += basicTestDB.chatRow,
          UsersTable.all += basicTestDB.viewerUserRow,
          UserChatsTable.all += basicTestDB.userChatRow.copy(inbox = 1),
          EmailsTable.all += basicTestDB.emailRow.copy(sent = 0),
          EmailAddressesTable.all ++= List(
            basicTestDB.emailAddressesRow.copy(participantType = "cc"),
            senderEmailAddressesRow)))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "Not detect an email addressed to the viewer, that has not been sent [Bcc]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val senderAddressRow = genAddressRow.sample.value
      val senderEmailAddressesRow = genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
        senderAddressRow.addressId, "from").sample.value

      val expectedChatsPreview = Seq.empty[ChatPreview]

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= List(basicTestDB.viewerAddressRow, senderAddressRow),
          ChatsTable.all += basicTestDB.chatRow,
          UsersTable.all += basicTestDB.viewerUserRow,
          UserChatsTable.all += basicTestDB.userChatRow.copy(inbox = 1),
          EmailsTable.all += basicTestDB.emailRow.copy(sent = 0),
          EmailAddressesTable.all ++= List(
            basicTestDB.emailAddressesRow.copy(participantType = "bcc"),
            senderEmailAddressesRow)))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "detect a chat if it is visible in the mailbox being used [Inbox]" in {
      val basicTestDB = genBasicTestDB.sample.value

      val expectedChatsPreview = Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.viewerAddressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all += basicTestDB.viewerAddressRow,
          ChatsTable.all += basicTestDB.chatRow,
          UsersTable.all += basicTestDB.viewerUserRow,
          UserChatsTable.all += basicTestDB.userChatRow.copy(inbox = 1),
          EmailsTable.all += basicTestDB.emailRow.copy(sent = 0),
          EmailAddressesTable.all += basicTestDB.emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "detect a chat if it is visible in the mailbox being used [Sent]" in {
      val basicTestDB = genBasicTestDB.sample.value

      val expectedChatsPreview = Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.viewerAddressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all += basicTestDB.viewerAddressRow,
          ChatsTable.all += basicTestDB.chatRow,
          UsersTable.all += basicTestDB.viewerUserRow,
          UserChatsTable.all += basicTestDB.userChatRow.copy(sent = 1),
          EmailsTable.all += basicTestDB.emailRow.copy(sent = 0),
          EmailAddressesTable.all += basicTestDB.emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Sent, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "detect a chat if it is visible in the mailbox being used [Drafts]" in {
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

    "detect a chat if it is visible in the mailbox being used [Trash]" in {
      val basicTestDB = genBasicTestDB.sample.value

      val expectedChatsPreview = Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.viewerAddressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all += basicTestDB.viewerAddressRow,
          ChatsTable.all += basicTestDB.chatRow,
          UsersTable.all += basicTestDB.viewerUserRow,
          UserChatsTable.all += basicTestDB.userChatRow.copy(trash = 1),
          EmailsTable.all += basicTestDB.emailRow.copy(sent = 0),
          EmailAddressesTable.all += basicTestDB.emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Trash, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "Not detect a chat if it isn't visible in the mailbox being used [Inbox]" in {
      val basicTestDB = genBasicTestDB.sample.value

      val expectedChatsPreview = Seq.empty[ChatPreview]

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all += basicTestDB.viewerAddressRow,
          ChatsTable.all += basicTestDB.chatRow,
          UsersTable.all += basicTestDB.viewerUserRow,
          UserChatsTable.all += basicTestDB.userChatRow,
          EmailsTable.all += basicTestDB.emailRow.copy(sent = 0),
          EmailAddressesTable.all += basicTestDB.emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "Not detect a chat if it isn't visible in the mailbox being used [Sent]" in {
      val basicTestDB = genBasicTestDB.sample.value

      val expectedChatsPreview = Seq.empty[ChatPreview]

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all += basicTestDB.viewerAddressRow,
          ChatsTable.all += basicTestDB.chatRow,
          UsersTable.all += basicTestDB.viewerUserRow,
          UserChatsTable.all += basicTestDB.userChatRow,
          EmailsTable.all += basicTestDB.emailRow.copy(sent = 0),
          EmailAddressesTable.all += basicTestDB.emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Sent, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "Not detect a chat if it isn't visible in the mailbox being used [Drafts]" in {
      val basicTestDB = genBasicTestDB.sample.value

      val expectedChatsPreview = Seq.empty[ChatPreview]

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all += basicTestDB.viewerAddressRow,
          ChatsTable.all += basicTestDB.chatRow,
          UsersTable.all += basicTestDB.viewerUserRow,
          UserChatsTable.all += basicTestDB.userChatRow,
          EmailsTable.all += basicTestDB.emailRow.copy(sent = 0),
          EmailAddressesTable.all += basicTestDB.emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Drafts, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "Not detect a chat if it isn't visible in the mailbox being used [Trash]" in {
      val basicTestDB = genBasicTestDB.sample.value

      val expectedChatsPreview = Seq.empty[ChatPreview]

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all += basicTestDB.viewerAddressRow,
          ChatsTable.all += basicTestDB.chatRow,
          UsersTable.all += basicTestDB.viewerUserRow,
          UserChatsTable.all += basicTestDB.userChatRow,
          EmailsTable.all += basicTestDB.emailRow.copy(sent = 0),
          EmailAddressesTable.all += basicTestDB.emailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Trash, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "show only the most recent email" in {
      val basicTestDB = genBasicTestDB.sample.value
      val oldEmailRow = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(date = "2018")
      val oldEmailAddressesRow = genEmailAddressRow(oldEmailRow.emailId, basicTestDB.chatRow.chatId,
        basicTestDB.viewerAddressRow.addressId, "from").sample.value
      val expectedChatsPreview = Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.viewerAddressRow.address, "2019", basicTestDB.emailRow.body))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all += basicTestDB.viewerAddressRow,
          ChatsTable.all += basicTestDB.chatRow,
          UsersTable.all += basicTestDB.viewerUserRow,
          UserChatsTable.all += basicTestDB.userChatRow.copy(inbox = 1),
          EmailsTable.all ++= List(basicTestDB.emailRow.copy(date = "2019"), oldEmailRow),
          EmailAddressesTable.all ++= List(basicTestDB.emailAddressesRow, oldEmailAddressesRow)))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "show only the email with the lowest Id in case the dates are repeated" in {
      val basicTestDB = genBasicTestDB.sample.value
      val otherEmailRow = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(date = "2019")
      val otherEmailAddressesRow = genEmailAddressRow(otherEmailRow.emailId, basicTestDB.chatRow.chatId,
        basicTestDB.viewerAddressRow.addressId, "from").sample.value

      val predictedEmail = List(basicTestDB.emailRow, otherEmailRow).minBy(_.emailId)

      val expectedChatsPreview = Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.viewerAddressRow.address, "2019", predictedEmail.body))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all += basicTestDB.viewerAddressRow,
          ChatsTable.all += basicTestDB.chatRow,
          UsersTable.all += basicTestDB.viewerUserRow,
          UserChatsTable.all += basicTestDB.userChatRow.copy(inbox = 1),
          EmailsTable.all ++= List(basicTestDB.emailRow.copy(date = "2019"), otherEmailRow),
          EmailAddressesTable.all ++= List(basicTestDB.emailAddressesRow, otherEmailAddressesRow)))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview

    }

    "detect more than one chat" in {
      val basicTestDB = genBasicTestDB.sample.value
      val otherChatRow = genChatRow.sample.value
      val otherUserChatRow = genUserChatRow(basicTestDB.viewerUserRow.userId, otherChatRow.chatId).sample.value
        .copy(inbox = 1)
      val otherEmailRow = genEmailRow(otherChatRow.chatId).sample.value
      val otherEmailAddressesRow = genEmailAddressRow(otherEmailRow.emailId, otherChatRow.chatId,
        basicTestDB.viewerAddressRow.addressId, "from").sample.value

      val expectedChatsPreview = List(
        ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
          basicTestDB.viewerAddressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body),
        ChatPreview(otherChatRow.chatId, otherChatRow.subject,
          basicTestDB.viewerAddressRow.address, otherEmailRow.date, otherEmailRow.body))
        .sortBy(chatPreview =>
          (chatPreview.lastEmailDate, chatPreview.contentPreview, chatPreview.lastAddress))(
          Ordering.Tuple3(Ordering.String.reverse, Ordering.String, Ordering.String))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all += basicTestDB.viewerAddressRow,
          ChatsTable.all ++= List(basicTestDB.chatRow, otherChatRow),
          UsersTable.all += basicTestDB.viewerUserRow,
          UserChatsTable.all ++= List(basicTestDB.userChatRow.copy(inbox = 1), otherUserChatRow),
          EmailsTable.all ++= List(basicTestDB.emailRow, otherEmailRow),
          EmailAddressesTable.all ++= List(basicTestDB.emailAddressesRow, otherEmailAddressesRow)))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "detect an oversee's email if it was sent" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseeAddressRow = genAddressRow.sample.value
      val overseeUserRow = genUserRow(overseeAddressRow.addressId).sample.value
      val oversightRow = genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId,
        overseeUserRow.userId).sample.value
      val overseeEmailRow = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(sent = 1)
      val overseeEmailAddressesRow = genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
        overseeAddressRow.addressId, "from").sample.value

      val expectedChatsPreview =
        Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
          overseeAddressRow.address, overseeEmailRow.date, overseeEmailRow.body))

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= List(basicTestDB.viewerAddressRow, overseeAddressRow),
          ChatsTable.all += basicTestDB.chatRow,
          UsersTable.all ++= List(basicTestDB.viewerUserRow, overseeUserRow),
          UserChatsTable.all += basicTestDB.userChatRow.copy(inbox = 1),
          OversightsTable.all += oversightRow,
          EmailsTable.all += overseeEmailRow,
          EmailAddressesTable.all += overseeEmailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

    "Not detect an oversee's email if it wasn't sent" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseeAddressRow = genAddressRow.sample.value
      val overseeUserRow = genUserRow(overseeAddressRow.addressId).sample.value
      val oversightRow = genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.viewerUserRow.userId,
        overseeUserRow.userId).sample.value
      val overseeEmailRow = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(sent = 0)
      val overseeEmailAddressesRow = genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
        overseeAddressRow.addressId, "from").sample.value

      val expectedChatsPreview = Seq.empty[ChatPreview]

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all ++= List(basicTestDB.viewerAddressRow, overseeAddressRow),
          ChatsTable.all += basicTestDB.chatRow,
          UsersTable.all ++= List(basicTestDB.viewerUserRow, overseeUserRow),
          UserChatsTable.all += basicTestDB.userChatRow.copy(inbox = 1),
          OversightsTable.all += oversightRow,
          EmailsTable.all += overseeEmailRow,
          EmailAddressesTable.all += overseeEmailAddressesRow))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.viewerUserRow.userId)
      } yield chatsPreview mustBe expectedChatsPreview
    }

  }

}

case class BasicTestDB(viewerAddressRow: AddressRow, viewerUserRow: UserRow, chatRow: ChatRow, emailRow: EmailRow,
  emailAddressesRow: EmailAddressRow, userChatRow: UserChatRow)

