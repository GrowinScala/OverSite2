package repositories.slick.implementations

import repositories.dtos.PatchChat.{ ChangeSubject, MoveToTrash, Restore }
import model.types.Mailbox.{ Drafts, Inbox, Sent }
import org.scalatest._
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import repositories.dtos._
import repositories.slick.mappings._
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.duration.Duration
import scala.concurrent._
import model.types.Mailbox._
import model.types.ParticipantType._
import repositories.dtos._
import repositories.slick.mappings._
import utils.TestGenerators._

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

  def fillDB(addressRows: List[AddressRow] = Nil, chatRows: List[ChatRow] = Nil, userRows: List[UserRow] = Nil,
    userChatRows: List[UserChatRow] = Nil, emailRows: List[EmailRow] = Nil,
    emailAddressRows: List[EmailAddressRow] = Nil, oversightRows: List[OversightRow] = Nil): Future[Unit] =
    db.run(DBIO.seq(
      AddressesTable.all ++= addressRows,
      ChatsTable.all ++= chatRows,
      UsersTable.all ++= userRows,
      UserChatsTable.all ++= userChatRows,
      EmailsTable.all ++= emailRows,
      EmailAddressesTable.all ++= emailAddressRows,
      OversightsTable.all ++= oversightRows))

  def addressesFromUpsertEmail(email: UpsertEmail): Set[String] = {
    val fromAddress = email.from match {
      case None => Set.empty[String]
      case Some(address) => Set(address)
    }

    fromAddress ++ email.to.getOrElse(Set.empty[String]) ++ email.cc.getOrElse(Set.empty[String]) ++
      email.bcc.getOrElse(Set.empty[String])
  }

  "SlickChatsRepository#getChatsPreview" should {
    "detect a draft made by the viewer " in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow.copy(draft = 1)),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(basicTestDB.emailAddressRow))

        chatsPreview <- chatsRep.getChatsPreview(Drafts, basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.addressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body))
    }

    "detect an email sent to the viewer [To]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val senderAddressRow = genAddressRow.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(sent = 1)),
          List(
            basicTestDB.emailAddressRow.copy(participantType = To),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, From).sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        senderAddressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body))

    }

    "detect an email sent to the viewer [CC]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val senderAddressRow = genAddressRow.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(sent = 1)),
          List(
            basicTestDB.emailAddressRow.copy(participantType = Cc),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, From).sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        senderAddressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body))
    }

    "detect an email sent to the viewer [BCC]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val senderAddressRow = genAddressRow.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(sent = 1)),
          List(
            basicTestDB.emailAddressRow.copy(participantType = Bcc),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, From).sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        senderAddressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body))
    }

    "Not detect an email addressed to the viewer, that has not been sent [To]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val senderAddressRow = genAddressRow.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(
            basicTestDB.emailAddressRow.copy(participantType = To),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, From).sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.userRow.userId)
      } yield chatsPreview mustBe empty
    }

    "Not detect an email addressed to the viewer, that has not been sent [CC]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val senderAddressRow = genAddressRow.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(
            basicTestDB.emailAddressRow.copy(participantType = Cc),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, From).sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.userRow.userId)
      } yield chatsPreview mustBe empty
    }

    "Not detect an email addressed to the viewer, that has not been sent [BCC]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val senderAddressRow = genAddressRow.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(
            basicTestDB.emailAddressRow.copy(participantType = Bcc),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, From).sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.userRow.userId)
      } yield chatsPreview mustBe empty
    }

    "detect a chat if it is visible in the mailbox being used [Inbox]" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(basicTestDB.emailAddressRow))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.addressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body))
    }

    "detect a chat if it is visible in the mailbox being used [Sent]" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow.copy(sent = 1)),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(basicTestDB.emailAddressRow))

        chatsPreview <- chatsRep.getChatsPreview(Sent, basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.addressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body))
    }

    "detect a chat if it is visible in the mailbox being used [Drafts]" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow.copy(draft = 1)),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(basicTestDB.emailAddressRow))

        chatsPreview <- chatsRep.getChatsPreview(Drafts, basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.addressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body))
    }

    "detect a chat if it is visible in the mailbox being used [Trash]" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow.copy(trash = 1)),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(basicTestDB.emailAddressRow))

        chatsPreview <- chatsRep.getChatsPreview(Trash, basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.addressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body))
    }

    "Not detect a chat if it isn't visible in the mailbox being used [Inbox]" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow.copy(inbox = 0)),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(basicTestDB.emailAddressRow))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.userRow.userId)
      } yield chatsPreview mustBe empty
    }

    "Not detect a chat if it isn't visible in the mailbox being used [Sent]" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(basicTestDB.emailAddressRow))

        chatsPreview <- chatsRep.getChatsPreview(Sent, basicTestDB.userRow.userId)
      } yield chatsPreview mustBe empty
    }

    "Not detect a chat if it isn't visible in the mailbox being used [Drafts]" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(basicTestDB.emailAddressRow))

        chatsPreview <- chatsRep.getChatsPreview(Drafts, basicTestDB.userRow.userId)
      } yield chatsPreview mustBe empty
    }

    "Not detect a chat if it isn't visible in the mailbox being used [Trash]" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(basicTestDB.emailAddressRow))

        chatsPreview <- chatsRep.getChatsPreview(Trash, basicTestDB.userRow.userId)
      } yield chatsPreview mustBe empty
    }

    "show only the most recent email" in {
      val basicTestDB = genBasicTestDB.sample.value
      val oldEmailRow = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(date = "2018")

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(date = "2019"), oldEmailRow),
          List(
            basicTestDB.emailAddressRow,
            genEmailAddressRow(oldEmailRow.emailId, basicTestDB.chatRow.chatId,
              basicTestDB.addressRow.addressId, From).sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.addressRow.address, "2019", basicTestDB.emailRow.body))
    }

    "show only one email even if there are multiple emails with the latest date." in {
      val basicTestDB = genBasicTestDB.sample.value
      val otherEmailRow = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(date = "2019")

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(date = "2019"), otherEmailRow),
          List(
            basicTestDB.emailAddressRow,
            genEmailAddressRow(otherEmailRow.emailId, basicTestDB.chatRow.chatId,
              basicTestDB.addressRow.addressId, From).sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.addressRow.address, "2019", List(basicTestDB.emailRow, otherEmailRow).minBy(_.emailId).body))

    }

    "detect more than one chat" in {
      val basicTestDB = genBasicTestDB.sample.value
      val otherChatRow = genChatRow.sample.value
      val otherEmailRow = genEmailRow(otherChatRow.chatId).sample.value
      val otherEmailAddressesRow = genEmailAddressRow(otherEmailRow.emailId, otherChatRow.chatId,
        basicTestDB.addressRow.addressId, From).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow, otherChatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow, genUserChatRow(
            basicTestDB.userRow.userId,
            otherChatRow.chatId).sample.value),
          List(basicTestDB.emailRow, otherEmailRow),
          List(
            basicTestDB.emailAddressRow,
            genEmailAddressRow(otherEmailRow.emailId, otherChatRow.chatId,
              basicTestDB.addressRow.addressId, From).sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.userRow.userId)
      } yield chatsPreview mustBe List(
        ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
          basicTestDB.addressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body),
        ChatPreview(otherChatRow.chatId, otherChatRow.subject,
          basicTestDB.addressRow.address, otherEmailRow.date, otherEmailRow.body))
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
          List(basicTestDB.addressRow, overseeAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow, overseeUserRow),
          List(basicTestDB.userChatRow),
          List(overseeEmailRow),
          List(genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
            overseeAddressRow.addressId, From).sample.value),
          List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId,
            overseeUserRow.userId).sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.userRow.userId)
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
          List(basicTestDB.addressRow, overseeAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow, overseeUserRow),
          List(basicTestDB.userChatRow),
          List(overseeEmailRow),
          List(genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
            overseeAddressRow.addressId, From).sample.value),
          List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId,
            overseeUserRow.userId).sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.userRow.userId)
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
          List(basicTestDB.addressRow, overseeAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow, overseeUserRow),
          List(basicTestDB.userChatRow),
          List(overseeEmailRow),
          List(
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              overseeAddressRow.addressId, To).sample.value,
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, From).sample.value),
          List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId,
            overseeUserRow.userId).sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.userRow.userId)
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
          List(basicTestDB.addressRow, overseeAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow, overseeUserRow),
          List(basicTestDB.userChatRow),
          List(overseeEmailRow),
          List(
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              overseeAddressRow.addressId, Cc).sample.value,
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, From).sample.value),
          List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId,
            overseeUserRow.userId).sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.userRow.userId)
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
          List(basicTestDB.addressRow, overseeAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow, overseeUserRow),
          List(basicTestDB.userChatRow),
          List(overseeEmailRow),
          List(
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              overseeAddressRow.addressId, Bcc).sample.value,
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, From).sample.value),
          List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId,
            overseeUserRow.userId).sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.userRow.userId)
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
          List(basicTestDB.addressRow, overseeAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow, overseeUserRow),
          List(basicTestDB.userChatRow),
          List(overseeEmailRow),
          List(
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              overseeAddressRow.addressId, To).sample.value,
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, From).sample.value),
          List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId,
            overseeUserRow.userId).sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.userRow.userId)
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
          List(basicTestDB.addressRow, overseeAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow, overseeUserRow),
          List(basicTestDB.userChatRow),
          List(overseeEmailRow),
          List(
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              overseeAddressRow.addressId, Cc).sample.value,
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, From).sample.value),
          List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId,
            overseeUserRow.userId).sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.userRow.userId)
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
          List(basicTestDB.addressRow, overseeAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow, overseeUserRow),
          List(basicTestDB.userChatRow),
          List(overseeEmailRow),
          List(
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              overseeAddressRow.addressId, Bcc).sample.value,
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, From).sample.value),
          List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId,
            overseeUserRow.userId).sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.userRow.userId)
      } yield chatsPreview mustBe empty
    }

  }

  "SlickChatsRepository#getChat" should {
    "Not detect a non existing chat" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(basicTestDB.emailAddressRow))

        optChat <- chatsRep.getChat(genUUID.sample.value, basicTestDB.userRow.userId)
      } yield optChat mustBe None
    }

    "Not detect a chat the user does not have access to" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(basicTestDB.emailAddressRow))

        optChat <- chatsRep.getChat(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId)
      } yield optChat mustBe None
    }

    "detect a draft made by the viewer " in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(sent = 0)),
          List(basicTestDB.emailAddressRow))

        optChat <- chatsRep.getChat(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId)
      } yield optChat mustBe Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(basicTestDB.addressRow.address), Set(),
        Seq(Email(basicTestDB.emailRow.emailId, basicTestDB.addressRow.address, Set(), Set(), Set(),
          basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 0, Set()))))
    }

    "detect only emails addressed to the viewer that were sent [To]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val senderAddressRow = genAddressRow.sample.value
      val notSentEmail = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(sent = 0)

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(sent = 1), notSentEmail),
          List(
            basicTestDB.emailAddressRow.copy(participantType = To),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, From).sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, From)
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, basicTestDB.addressRow.addressId,
              To).sample.value))

        optChat <- chatsRep.getChat(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId)
      } yield optChat mustBe Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(basicTestDB.addressRow.address, senderAddressRow.address), Set(),
        Seq(Email(basicTestDB.emailRow.emailId, senderAddressRow.address,
          Set(basicTestDB.addressRow.address), Set(), Set(),
          basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 1, Set()))))

    }

    "detect only emails addressed to the viewer that were sent [CC]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val senderAddressRow = genAddressRow.sample.value
      val notSentEmail = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(sent = 0)

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(sent = 1), notSentEmail),
          List(
            basicTestDB.emailAddressRow.copy(participantType = Cc),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, From).sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, From)
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, basicTestDB.addressRow.addressId,
              Cc).sample.value))

        optChat <- chatsRep.getChat(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId)
      } yield optChat mustBe Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(basicTestDB.addressRow.address, senderAddressRow.address), Set(),
        Seq(Email(basicTestDB.emailRow.emailId, senderAddressRow.address,
          Set(), Set(), Set(basicTestDB.addressRow.address),
          basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 1, Set()))))
    }

    "detect only emails addressed to the viewer that were sent [BCC]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val senderAddressRow = genAddressRow.sample.value
      val notSentEmail = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(sent = 0)

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(sent = 1), notSentEmail),
          List(
            basicTestDB.emailAddressRow.copy(participantType = Bcc),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, From).sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, From)
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, basicTestDB.addressRow.addressId,
              Bcc).sample.value))

        optChat <- chatsRep.getChat(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId)
      } yield optChat mustBe Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(basicTestDB.addressRow.address, senderAddressRow.address), Set(),
        Seq(Email(basicTestDB.emailRow.emailId, senderAddressRow.address,
          Set(), Set(basicTestDB.addressRow.address), Set(),
          basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 1, Set()))))
    }

    "show the emails ordered by date" in {
      val basicTestDB = genBasicTestDB.sample.value
      val oldEmailRow = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(date = "2018")

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow.copy(date = "2019"), oldEmailRow),
          List(
            basicTestDB.emailAddressRow,
            genEmailAddressRow(oldEmailRow.emailId, basicTestDB.chatRow.chatId,
              basicTestDB.addressRow.addressId, From).sample.value))

        optChat <- chatsRep.getChat(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId)
      } yield optChat mustBe Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(basicTestDB.addressRow.address), Set(),
        Seq(
          Email(oldEmailRow.emailId, basicTestDB.addressRow.address, Set(), Set(), Set(),
            oldEmailRow.body, oldEmailRow.date, oldEmailRow.sent, Set()),
          Email(basicTestDB.emailRow.emailId, basicTestDB.addressRow.address, Set(), Set(), Set(),
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
          List(basicTestDB.addressRow, overseeAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow, overseeUserRow),
          List(basicTestDB.userChatRow),
          List(sentEmail, notSentEmail),
          List(
            genEmailAddressRow(sentEmail.emailId, basicTestDB.chatRow.chatId, overseeAddressRow.addressId, From)
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, overseeAddressRow.addressId, From)
              .sample.value),
          List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId,
            overseeUserRow.userId).sample.value))

        optChat <- chatsRep.getChat(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId)
      } yield optChat mustBe Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(overseeAddressRow.address),
        Set(Overseers(overseeAddressRow.address, Set(basicTestDB.addressRow.address))),
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
          List(basicTestDB.addressRow, overseeAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow, overseeUserRow),
          List(basicTestDB.userChatRow),
          List(sentEmail, notSentEmail),
          List(
            genEmailAddressRow(sentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, From)
              .sample.value,
            genEmailAddressRow(sentEmail.emailId, basicTestDB.chatRow.chatId, overseeAddressRow.addressId, To)
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, From)
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, overseeAddressRow.addressId, To)
              .sample.value),
          List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId,
            overseeUserRow.userId).sample.value))

        optChat <- chatsRep.getChat(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId)
      } yield optChat mustBe Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(overseeAddressRow.address, senderAddressRow.address),
        Set(Overseers(overseeAddressRow.address, Set(basicTestDB.addressRow.address))),
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
          List(basicTestDB.addressRow, overseeAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow, overseeUserRow),
          List(basicTestDB.userChatRow),
          List(sentEmail, notSentEmail),
          List(
            genEmailAddressRow(sentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, From)
              .sample.value,
            genEmailAddressRow(sentEmail.emailId, basicTestDB.chatRow.chatId, overseeAddressRow.addressId, Cc)
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, From)
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, overseeAddressRow.addressId, Cc)
              .sample.value),
          List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId,
            overseeUserRow.userId).sample.value))

        optChat <- chatsRep.getChat(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId)
      } yield optChat mustBe Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(overseeAddressRow.address, senderAddressRow.address),
        Set(Overseers(overseeAddressRow.address, Set(basicTestDB.addressRow.address))),
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
          List(basicTestDB.addressRow, overseeAddressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow, overseeUserRow),
          List(basicTestDB.userChatRow),
          List(sentEmail, notSentEmail),
          List(
            genEmailAddressRow(sentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, From)
              .sample.value,
            genEmailAddressRow(sentEmail.emailId, basicTestDB.chatRow.chatId, overseeAddressRow.addressId, Bcc)
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, From)
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, overseeAddressRow.addressId, Bcc)
              .sample.value),
          List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId,
            overseeUserRow.userId).sample.value))

        optChat <- chatsRep.getChat(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId)
      } yield optChat mustBe Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(overseeAddressRow.address, senderAddressRow.address),
        Set(Overseers(overseeAddressRow.address, Set(basicTestDB.addressRow.address))),
        Seq(Email(sentEmail.emailId, senderAddressRow.address, Set(), Set(overseeAddressRow.address), Set(),
          sentEmail.body, sentEmail.date, sent = 1, Set()))))

    }

    "show a bcc address to an Overseer only if their Oversee is the sender of the email " +
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
          Set(basicTestDB.addressRow.address, toAddressRow.address, ccAddressRow.address, bccAddressRow.address),
          Set(
            Overseers(basicTestDB.addressRow.address, Set(fromOverseerAddressRow.address)),
            Overseers(toAddressRow.address, Set(toOverseerAddressRow.address)),
            Overseers(ccAddressRow.address, Set(ccOverseerAddressRow.address)),
            Overseers(bccAddressRow.address, Set(bccOverseerAddressRow.address))),
          Seq(Email(basicTestDB.emailRow.emailId, basicTestDB.addressRow.address,
            Set(toAddressRow.address), Set(bccAddressRow.address), Set(ccAddressRow.address),
            basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 1, Set()))))

        val notVisibleBCCOptChat = Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
          Set(basicTestDB.addressRow.address, toAddressRow.address, ccAddressRow.address),
          Set(
            Overseers(basicTestDB.addressRow.address, Set(fromOverseerAddressRow.address)),
            Overseers(toAddressRow.address, Set(toOverseerAddressRow.address)),
            Overseers(ccAddressRow.address, Set(ccOverseerAddressRow.address)),
            Overseers(bccAddressRow.address, Set(bccOverseerAddressRow.address))),
          Seq(Email(basicTestDB.emailRow.emailId, basicTestDB.addressRow.address,
            Set(toAddressRow.address), Set(), Set(ccAddressRow.address),
            basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 1, Set()))))

        for {
          _ <- fillDB(
            List(basicTestDB.addressRow, toAddressRow, ccAddressRow, bccAddressRow, fromOverseerAddressRow,
              toOverseerAddressRow, ccOverseerAddressRow, bccOverseerAddressRow),
            List(basicTestDB.chatRow),
            List(basicTestDB.userRow, toUserRow, ccUserRow, bccUserRow, fromOverseerUserRow, toOverseerUserRow,
              ccOverseerUserRow, bccOverseerUserRow),
            List(basicTestDB.userChatRow, genUserChatRow(toUserRow.userId, basicTestDB.chatRow.chatId).sample.value,
              genUserChatRow(ccUserRow.userId, basicTestDB.chatRow.chatId).sample.value,
              genUserChatRow(bccUserRow.userId, basicTestDB.chatRow.chatId).sample.value,
              genUserChatRow(fromOverseerUserRow.userId, basicTestDB.chatRow.chatId).sample.value,
              genUserChatRow(toOverseerUserRow.userId, basicTestDB.chatRow.chatId).sample.value,
              genUserChatRow(ccOverseerUserRow.userId, basicTestDB.chatRow.chatId).sample.value,
              genUserChatRow(bccOverseerUserRow.userId, basicTestDB.chatRow.chatId).sample.value),
            List(basicTestDB.emailRow.copy(sent = 1)),
            List(
              basicTestDB.emailAddressRow,
              genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
                toAddressRow.addressId, To).sample.value,
              genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
                ccAddressRow.addressId, Cc).sample.value,
              genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
                bccAddressRow.addressId, Bcc).sample.value),
            List(
              genOversightRow(basicTestDB.chatRow.chatId, fromOverseerUserRow.userId, basicTestDB.userRow.userId)
                .sample.value,
              genOversightRow(basicTestDB.chatRow.chatId, toOverseerUserRow.userId, toUserRow.userId)
                .sample.value,
              genOversightRow(basicTestDB.chatRow.chatId, ccOverseerUserRow.userId, ccUserRow.userId)
                .sample.value,
              genOversightRow(basicTestDB.chatRow.chatId, bccOverseerUserRow.userId, bccUserRow.userId)
                .sample.value))

          optChatOverFrom <- chatsRep.getChat(basicTestDB.chatRow.chatId, fromOverseerUserRow.userId)
          optChatOverTo <- chatsRep.getChat(basicTestDB.chatRow.chatId, toOverseerUserRow.userId)
          optChatOverCC <- chatsRep.getChat(basicTestDB.chatRow.chatId, ccOverseerUserRow.userId)
          optChatOverBCC <- chatsRep.getChat(basicTestDB.chatRow.chatId, bccOverseerUserRow.userId)

        } yield assert(
          optChatOverFrom === visibleBCCOptChat &&
            optChatOverTo === notVisibleBCCOptChat &&
            optChatOverCC === notVisibleBCCOptChat &&
            optChatOverBCC === visibleBCCOptChat)

      }

    "show all bcc addresses to the Overseer of the sender of the email, but the Overseers of each each bcc user" +
      " can only see said user's address" in {
        val basicTestDB = genBasicTestDB.sample.value
        val bccOneAddressRow = genAddressRow.sample.value
        val bccTwoAddressRow = genAddressRow.sample.value
        val fromOverseerAddressRow = genAddressRow.sample.value
        val bccOneOverseerAddressRow = genAddressRow.sample.value
        val bccTwoOverseerAddressRow = genAddressRow.sample.value
        val bccOneUserRow = genUserRow(bccOneAddressRow.addressId).sample.value
        val bccTwoUserRow = genUserRow(bccTwoAddressRow.addressId).sample.value
        val fromOverseerUserRow = genUserRow(fromOverseerAddressRow.addressId).sample.value
        val bccOneOverseerUserRow = genUserRow(bccOneOverseerAddressRow.addressId).sample.value
        val bccTwoOverseerUserRow = genUserRow(bccTwoOverseerAddressRow.addressId).sample.value

        for {
          _ <- fillDB(
            List(basicTestDB.addressRow, bccOneAddressRow, bccTwoAddressRow, fromOverseerAddressRow,
              bccOneOverseerAddressRow,
              bccTwoOverseerAddressRow),
            List(basicTestDB.chatRow),
            List(basicTestDB.userRow, bccOneUserRow, bccTwoUserRow, fromOverseerUserRow, bccOneOverseerUserRow,
              bccTwoOverseerUserRow),
            List(basicTestDB.userChatRow, genUserChatRow(bccOneUserRow.userId, basicTestDB.chatRow.chatId).sample.value,
              genUserChatRow(bccTwoUserRow.userId, basicTestDB.chatRow.chatId).sample.value,
              genUserChatRow(fromOverseerUserRow.userId, basicTestDB.chatRow.chatId).sample.value,
              genUserChatRow(bccOneOverseerUserRow.userId, basicTestDB.chatRow.chatId).sample.value,
              genUserChatRow(bccTwoOverseerUserRow.userId, basicTestDB.chatRow.chatId).sample.value),
            List(basicTestDB.emailRow.copy(sent = 1)),
            List(
              basicTestDB.emailAddressRow,
              genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
                bccOneAddressRow.addressId, Bcc).sample.value,
              genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
                bccTwoAddressRow.addressId, Bcc).sample.value),
            List(
              genOversightRow(basicTestDB.chatRow.chatId, fromOverseerUserRow.userId, basicTestDB.userRow.userId)
                .sample.value,
              genOversightRow(basicTestDB.chatRow.chatId, bccOneOverseerUserRow.userId, bccOneUserRow.userId)
                .sample.value,
              genOversightRow(basicTestDB.chatRow.chatId, bccTwoOverseerUserRow.userId, bccTwoUserRow.userId)
                .sample.value))

          optChatOverFrom <- chatsRep.getChat(basicTestDB.chatRow.chatId, fromOverseerUserRow.userId)
          optChatOverBCCOne <- chatsRep.getChat(basicTestDB.chatRow.chatId, bccOneOverseerUserRow.userId)
          optChatOverBCCTwo <- chatsRep.getChat(basicTestDB.chatRow.chatId, bccTwoOverseerUserRow.userId)

        } yield assert(
          optChatOverFrom === Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
            Set(basicTestDB.addressRow.address, bccOneAddressRow.address, bccTwoAddressRow.address),
            Set(
              Overseers(basicTestDB.addressRow.address, Set(fromOverseerAddressRow.address)),
              Overseers(bccOneAddressRow.address, Set(bccOneOverseerAddressRow.address)),
              Overseers(bccTwoAddressRow.address, Set(bccTwoOverseerAddressRow.address))),
            Seq(Email(basicTestDB.emailRow.emailId, basicTestDB.addressRow.address,
              Set(), Set(bccOneAddressRow.address, bccTwoAddressRow.address), Set(),
              basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 1, Set()))))
            &&
            optChatOverBCCOne === Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
              Set(basicTestDB.addressRow.address, bccOneAddressRow.address),
              Set(
                Overseers(basicTestDB.addressRow.address, Set(fromOverseerAddressRow.address)),
                Overseers(bccOneAddressRow.address, Set(bccOneOverseerAddressRow.address)),
                Overseers(bccTwoAddressRow.address, Set(bccTwoOverseerAddressRow.address))),
              Seq(Email(basicTestDB.emailRow.emailId, basicTestDB.addressRow.address,
                Set(), Set(bccOneAddressRow.address), Set(),
                basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 1, Set()))))
            &&
            optChatOverBCCTwo === Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
              Set(basicTestDB.addressRow.address, bccTwoAddressRow.address),
              Set(
                Overseers(basicTestDB.addressRow.address, Set(fromOverseerAddressRow.address)),
                Overseers(bccOneAddressRow.address, Set(bccOneOverseerAddressRow.address)),
                Overseers(bccTwoAddressRow.address, Set(bccTwoOverseerAddressRow.address))),
              Seq(Email(basicTestDB.emailRow.emailId, basicTestDB.addressRow.address,
                Set(), Set(bccTwoAddressRow.address), Set(),
                basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 1, Set())))))

      }

  }

  "SlickChatsRepository#postChat" should {

    "not post the chat if the userId does not have a corresponding address" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          userRows = List(basicTestDB.userRow))
        postResponse <- chatsRep.postChat(genCreateChatDTOption.sample.value, basicTestDB.userRow.userId)
      } yield postResponse mustBe None

    }

    "create a chat with an email draft for a user and then get the same chat for the same user: results must match" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))
        postResponse <- chatsRep.postChat(genCreateChatOption.sample.value, basicTestDB.userRow.userId)
        getResponse <- chatsRep.getChat(postResponse.chatId.get, basicTestDB.userRow.userId)
      } yield getResponse mustBe Some(CreateChat.fromCreateChatToChat(postResponse))

    }

    "Not give access to the chat to a user that is a receiver of the email [To]" +
      "\n Note: This method only creates Drafts" in {
        val basicTestDB = genBasicTestDB.sample.value
        val receiverAddressRow = genAddressRow.sample.value
        val receiverUserRow = genUserRow(receiverAddressRow.addressId).sample.value
        val origCreateChatDTO = genCreateChatOption.sample.value

        for {
          _ <- fillDB(
            List(basicTestDB.addressRow, receiverAddressRow),
            userRows = List(basicTestDB.userRow, receiverUserRow))
          postResponse <- chatsRep.postChat(
            origCreateChatDTO.copy(email = origCreateChatDTO.email.copy(to = Some(Set(receiverAddressRow.address)))),
            basicTestDB.userRow.userId)
          getResponse <- chatsRep.getChat(postResponse.value.chatId.value, receiverUserRow.userId)
        } yield getResponse mustBe None

      }

    "Not give access to the chat to a user that is a receiver of the email [CC]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val receiverAddressRow = genAddressRow.sample.value
      val receiverUserRow = genUserRow(receiverAddressRow.addressId).sample.value
      val origCreateChat = genCreateChatOption.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, receiverAddressRow),
          userRows = List(basicTestDB.userRow, receiverUserRow))
        postResponse <- chatsRep.postChat(
          origCreateChat.copy(email = origCreateChat.email.copy(cc = Some(Set(receiverAddressRow.address)))),
          basicTestDB.userRow.userId)
        getResponse <- chatsRep.getChat(postResponse.value.chatId.value, receiverUserRow.userId)
      } yield getResponse mustBe None

    }

    "Not give access to the chat to a user that is a receiver of the email [BCC]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val receiverAddressRow = genAddressRow.sample.value
      val receiverUserRow = genUserRow(receiverAddressRow.addressId).sample.value
      val origCreateChatDTO = genCreateChatOption.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, receiverAddressRow),
          userRows = List(basicTestDB.userRow, receiverUserRow))
        postResponse <- chatsRep.postChat(
          origCreateChatDTO.copy(email = origCreateChatDTO.email.copy(bcc = Some(Set(receiverAddressRow.address)))),
          basicTestDB.userRow.userId)
        getResponse <- chatsRep.getChat(postResponse.value.chatId.value, receiverUserRow.userId)
      } yield getResponse mustBe None

    }

    "create a chat with an EMPTY draft for a user and then get the same chat for the same user: results must match" in {
      val basicTestDB = genBasicTestDB.sample.value

      val chatWithEmptyDraft =
        CreateChat(
          chatId = None,
          subject = None,
          UpsertEmail(
            emailId = None,
            from = None,
            to = None,
            bcc = None,
            cc = None,
            body = None,
            date = None,
            sent = None))

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))
        postResponse <- chatsRep.postChat(chatWithEmptyDraft, basicTestDB.userRow.userId)
        getResponse <- chatsRep.getChat(postResponse.chatId.get, basicTestDB.userRow.userId)
      } yield getResponse mustBe Some(CreateChat.fromCreateChatToChat(postResponse))

    }

  }

  "SlickChatsRepository#postEmail" should {

    "add an email draft to a chat and then get the same chat with the added email" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))
        postChatResponse <- chatsRep.postChat(genCreateChatOption.sample.value, basicTestDB.userRow.userId)
        postEmailResponse <- chatsRep.postEmail(genUpsertEmailOption.sample.value, postChatResponse.chatId.value,
          basicTestDB.userRow.userId)
        getResponse <- chatsRep.getChat(postChatResponse.value.chatId.value, basicTestDB.userRow.userId)
        nrDrafts <- db.run(UserChatsTable.all.filter(_.userId === basicTestDB.userRow.userId).map(_.draft)
          .result.headOption)

      } yield assert(getResponse.value === {
        val originalChat = CreateChat.fromCreateChatToChat(postChatResponse)
        originalChat.copy(
          emails = (UpsertEmail.fromUpsertEmailToEmail(postEmailResponse.value.email) +: originalChat.emails)
            .sortBy(email => (email.date, email.emailId)),
          addresses = addressesFromUpsertEmail(postEmailResponse.value.email) ++ originalChat.addresses)
      } && nrDrafts.value === 2)

    }

  }

  "SlickChatsRepository#patchEmail" should {

    "patch the body of an email in draft state" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          addressRows = List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))
        postChat <- chatsRep.postChat(genCreateChatOption.sample.value, basicTestDB.userRow.userId)
        getPostedEmail = CreateChat.fromCreateChatToChat(postChat).emails.headOption.value

        patchBody = genString.sample.value

        patchEmail <- chatsRep.patchEmail(
          UpsertEmail(None, None, None, None, None, Some(patchBody), None, None),
          postChat.chatId.value, postChat.email.emailId.value, basicTestDB.userRow.userId)
      } yield patchEmail.value mustBe getPostedEmail.copy(body = patchBody, date = patchEmail.value.date)
    }

    "patch all the email addresses, and send the email." +
      " The chat must appear in the sender and receivers' correct mailboxes" in {
        val basicTestDB = genBasicTestDB.sample.value
        val toAddressRow = genAddressRow.sample.value
        val ccAddressRow = genAddressRow.sample.value
        val bccAddressRow = genAddressRow.sample.value
        val toUserRow = genUserRow(toAddressRow.addressId).sample.value
        val ccUserRow = genUserRow(ccAddressRow.addressId).sample.value
        val bccUserRow = genUserRow(bccAddressRow.addressId).sample.value

        for {
          _ <- fillDB(
            addressRows = List(basicTestDB.addressRow, toAddressRow, ccAddressRow, bccAddressRow),
            userRows = List(basicTestDB.userRow, toUserRow, ccUserRow, bccUserRow))
          postChat <- chatsRep.postChat(genCreateChatOption.sample.value, basicTestDB.userRow.userId)
          getPostedChat = CreateChat.fromCreateChatToChat(postChat)
          getPostedEmail = CreateChat.fromCreateChatToChat(postChat).emails.headOption.value

          patchEmail <- chatsRep.patchEmail(
            UpsertEmail(None, None, Some(Set(toAddressRow.address)),
              Some(Set(bccAddressRow.address)), Some(Set(ccAddressRow.address)), None, None, Some(true)),
            postChat.value.chatId.value, postChat.value.email.emailId.value,
            basicTestDB.userRow.userId)

          fromUserGetChat <- chatsRep.getChat(postChat.value.chatId.value, basicTestDB.userRow.userId)
          toUserGetChat <- chatsRep.getChat(postChat.value.chatId.value, toUserRow.userId)
          ccUserGetChat <- chatsRep.getChat(postChat.value.chatId.value, ccUserRow.userId)
          bccUserGetChat <- chatsRep.getChat(postChat.value.chatId.value, bccUserRow.userId)

          senderChatsPreviewSent <- chatsRep.getChatsPreview(Sent, basicTestDB.userRow.userId)
          senderChatsPreviewDrafts <- chatsRep.getChatsPreview(Drafts, basicTestDB.userRow.userId)

          toReceiverChatsPreviewInbox <- chatsRep.getChatsPreview(Inbox, toUserRow.userId)
          ccReceiverChatsPreviewInbox <- chatsRep.getChatsPreview(Inbox, ccUserRow.userId)
          bccReceiverChatsPreviewInbox <- chatsRep.getChatsPreview(Inbox, bccUserRow.userId)

          invisibleBccExpectedEmailAfterPatch = getPostedEmail.copy(
            to = Set(toAddressRow.address),
            cc = Set(ccAddressRow.address), bcc = Set(), sent = 1, date = patchEmail.value.date)

          visibleBccExpectedEmailAfterPatch = invisibleBccExpectedEmailAfterPatch.copy(bcc = Set(bccAddressRow.address))

          invisibleBccExpectedChatAfterPatch = getPostedChat.copy(
            addresses = Set(basicTestDB.addressRow.address, toAddressRow.address, ccAddressRow.address),
            emails = Seq(invisibleBccExpectedEmailAfterPatch))

          visibleBccExpectedChatAfterPatch = invisibleBccExpectedChatAfterPatch.copy(
            addresses =
              invisibleBccExpectedChatAfterPatch.addresses + bccAddressRow.address,
            emails = Seq(visibleBccExpectedEmailAfterPatch))

          expectedChatPreview = ChatPreview(getPostedChat.chatId, getPostedChat.subject, basicTestDB.addressRow.address,
            patchEmail.value.date, patchEmail.value.body)

        } yield assert(
          patchEmail.value === visibleBccExpectedEmailAfterPatch &&
            fromUserGetChat.value === visibleBccExpectedChatAfterPatch &&
            toUserGetChat.value === invisibleBccExpectedChatAfterPatch &&
            ccUserGetChat.value === invisibleBccExpectedChatAfterPatch &&
            bccUserGetChat.value === visibleBccExpectedChatAfterPatch &&

            senderChatsPreviewSent.contains(expectedChatPreview) &&
            !senderChatsPreviewDrafts.contains(expectedChatPreview) &&

            toReceiverChatsPreviewInbox.contains(expectedChatPreview) &&
            ccReceiverChatsPreviewInbox.contains(expectedChatPreview) &&
            bccReceiverChatsPreviewInbox.contains(expectedChatPreview))
      }

    "not send an email if the receivers list (to + cc + bcc) is empty" in {
      val basicTestDB = genBasicTestDB.sample.value
      val origCreateChatDTO = genCreateChatOption.sample.value

      for {
        _ <- fillDB(
          addressRows = List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))
        postChat <- chatsRep.postChat(
          origCreateChatDTO
            .copy(email = origCreateChatDTO.email.copy(from = None, to = None, cc = None, bcc = None)),
          basicTestDB.userRow.userId)
        patchEmail <- chatsRep.patchEmail(
          UpsertEmail(None, None, None, None, None, None, None, Some(true)),
          postChat.chatId.value, postChat.email.emailId.value, basicTestDB.userRow.userId)
      } yield assert(
        patchEmail.value === CreateChat.fromCreateChatToChat(postChat).emails.headOption.value &&
          patchEmail.value.sent === 0)
    }

    "not allow an email patch if the user requesting it is not the its owner (from)" in {
      val basicTestDB = genBasicTestDB.sample.value
      val otherAddressRow = genAddressRow.sample.value
      val otherUserRow = genUserRow(otherAddressRow.addressId).sample.value
      val origCreateChatDTO = genCreateChatOption.sample.value

      for {
        _ <- fillDB(
          addressRows = List(basicTestDB.addressRow, otherAddressRow),
          userRows = List(basicTestDB.userRow, otherUserRow))
        postChat <- chatsRep.postChat(origCreateChatDTO.copy(email = origCreateChatDTO.email.copy(to =
          Some(Set(otherAddressRow.address)))), basicTestDB.userRow.userId)
        getPostedEmail = CreateChat.fromCreateChatToChat(postChat).emails.headOption.value

        patchEmail <- chatsRep.patchEmail(
          UpsertEmail(None, None, None, None, None, Some(genString.sample.value), None, None),
          postChat.chatId.value, postChat.email.emailId.value, otherUserRow.userId)
      } yield patchEmail mustBe None

    }

    "not allow an email patch if the email was already sent" in {
      val basicTestDB = genBasicTestDB.sample.value
      val origCreateChatDTO = genCreateChatOption.sample.value

      for {
        _ <- fillDB(
          addressRows = List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))
        postChat <- chatsRep.postChat(origCreateChatDTO.copy(email = origCreateChatDTO.email
          .copy(to = Some(Set(basicTestDB.addressRow.address)))), basicTestDB.userRow.userId)
        getPostedEmail = CreateChat.fromCreateChatToChat(postChat).emails.headOption.value

        patchEmail <- chatsRep.patchEmail(
          UpsertEmail(None, None, None, None, None, None, None, Some(true)),
          postChat.chatId.value, postChat.email.emailId.value, basicTestDB.userRow.userId)

        retryPatchEmailAfterSent <- chatsRep.patchEmail(
          UpsertEmail(None, None, None, None, None, Some(genString.sample.value), None, None),
          postChat.chatId.value, postChat.email.emailId.value, basicTestDB.userRow.userId)

      } yield retryPatchEmailAfterSent mustBe None
    }

    "return None if the requested emailId is not a part of the chat with the specified chatId" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          addressRows = List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))
        postChat <- chatsRep.postChat(genCreateChatOption.sample.value, basicTestDB.userRow.userId)
        createdChatId = postChat.chatId.value

        patchEmail <- chatsRep.patchEmail(
          UpsertEmail(None, None, None, None, None, Some(genString.sample.value), None, None),
          postChat.chatId.value, genUUID.sample.value, basicTestDB.userRow.userId)
      } yield patchEmail mustBe None

    }

  }

  "SlickChatsRepository#patchChat" should {

    "remove the user's chat from inbox, sent and draft and move it to trash" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow.copy(sent = 1, draft = 1)))
        result <- chatsRep.patchChat(MoveToTrash, basicTestDB.chatRow.chatId, basicTestDB.userRow.userId)
        optUserChat <- db.run(UserChatsTable.all.filter(uc => uc.chatId === basicTestDB.chatRow.chatId
          && uc.userId === basicTestDB.userRow.userId)
          .result.headOption)
        userChat = optUserChat.value
      } yield assert(
        result === Some(MoveToTrash) &&
          userChat.inbox === 0 &&
          userChat.sent === 0 &&
          userChat.draft === 0 &&
          userChat.trash === 1)

    }

    "restore the user's chat if it is already in trash" in {
      val basicTestDB = genBasicTestDB.sample.value
      val origCreateChatDTO = genCreateChatOption.sample.value

      for {
        _ <- fillDB(
          addressRows = List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))
        createdChatDTO <- chatsRep.postChat(origCreateChatDTO.copy(email = origCreateChatDTO.email
          .copy(to = Some(Set(basicTestDB.addressRow.address)))), basicTestDB.userRow.userId)
        _ <- chatsRep.postEmail(genUpsertEmailOption.sample.value, createdChatDTO.chatId.value,
          basicTestDB.userRow.userId)
        _ <- chatsRep.patchEmail(
          UpsertEmail(None, None, None, None, None, None, None, Some(true)),
          createdChatDTO.chatId.value, createdChatDTO.email.emailId.value, basicTestDB.userRow.userId)
        _ <- chatsRep.patchChat(MoveToTrash, createdChatDTO.chatId.value, basicTestDB.userRow.userId)
        result <- chatsRep.patchChat(Restore, createdChatDTO.chatId.value, basicTestDB.userRow.userId)
        optUserChat <- db.run(UserChatsTable.all.filter(uc => uc.chatId === createdChatDTO.chatId.value
          && uc.userId === basicTestDB.userRow.userId)
          .result.headOption)
        userChat = optUserChat.value
      } yield assert(
        result === Some(Restore) &&
          userChat.inbox === 1 &&
          userChat.sent === 1 &&
          userChat.draft === 1 &&
          userChat.trash === 0)
    }

    "restore a chat in which the user is an overseer" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseeAddressRow = genAddressRow.sample.value
      val overseeUserRow = genUserRow(overseeAddressRow.addressId).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, overseeAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow, overseeUserRow),
          List(
            basicTestDB.userChatRow.copy(inbox = 0, trash = 1),
            genUserChatRow(overseeUserRow.userId, basicTestDB.chatRow.chatId).sample.value),
          oversightRows = List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId,
            overseeUserRow.userId).sample.value))

        result <- chatsRep.patchChat(Restore, basicTestDB.chatRow.chatId, basicTestDB.userRow.userId)
        optUserChat <- db.run(UserChatsTable.all.filter(uc => uc.chatId === basicTestDB.chatRow.chatId &&
          uc.userId === basicTestDB.userRow.userId).result.headOption)
        userChat = optUserChat.value
      } yield assert(
        result === Some(Restore) &&
          userChat.inbox === 1 &&
          userChat.sent === 0 &&
          userChat.draft === 0 &&
          userChat.trash === 0)

    }

    "return None if the user does not have a chat with that id" in {
      val basicTestDB = genBasicTestDB.sample.value
      val invalidChatId = genUUID.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow.copy(sent = 1, draft = 1)))
        result <- chatsRep.patchChat(MoveToTrash, invalidChatId, basicTestDB.userRow.userId)
        optUserChat <- db.run(UserChatsTable.all.filter(uc => uc.chatId === invalidChatId &&
          uc.userId === basicTestDB.userRow.userId)
          .result.headOption)
      } yield assert(result === None && optUserChat === None)

    }

    "only change the chat's subject if the chat only has one email, it is a draft and the user is its owner" in {
      val basicTestDB = genBasicTestDB.sample.value
      val origCreateChatDTO = genCreateChatOption.sample.value

      for {
        _ <- fillDB(
          addressRows = List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))
        createdChatDTO <- chatsRep.postChat(origCreateChatDTO.copy(email = origCreateChatDTO.email
          .copy(to = Some(Set(basicTestDB.addressRow.address)))), basicTestDB.userRow.userId)

        newSubject = genString.sample.value
        result <- chatsRep.patchChat(ChangeSubject(newSubject), createdChatDTO.value.chatId.value,
          basicTestDB.userRow.userId)
        getPatchedChat <- chatsRep.getChat(createdChatDTO.value.chatId.value, basicTestDB.userRow.userId)

      } yield assert(
        result === Some(ChangeSubject(newSubject)) &&
          getPatchedChat.value.subject === newSubject)
    }

    "NOT change the chat's subject if the chat has more than one sent emails and return None" in {
      val basicTestDB = genBasicTestDB.sample.value
      val origCreateChatDTO = genCreateChatOption.sample.value

      for {
        _ <- fillDB(
          addressRows = List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))

        createdChatDTO <- chatsRep.postChat(origCreateChatDTO.copy(email = origCreateChatDTO.email
          .copy(to = Some(Set(basicTestDB.addressRow.address)))), basicTestDB.userRow.userId)

        sendEmail <- chatsRep.patchEmail(
          UpsertEmail(None, None, None, None, None, None, None, Some(true)),
          createdChatDTO.chatId.value, createdChatDTO.email.emailId.value, basicTestDB.userRow.userId)

        oldSubject = createdChatDTO.subject.getOrElse("")
        result <- chatsRep.patchChat(ChangeSubject(genString.sample.value), createdChatDTO.chatId.value, basicTestDB.userRow.userId)
        getChat <- chatsRep.getChat(createdChatDTO.chatId.value, basicTestDB.userRow.userId)

      } yield assert(result === None &&
        getChat.value.subject === oldSubject)
    }
  }

  "SlickChatsRepository#deleteChat" should {

    "definitely delete a chat from trash" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow.copy(inbox = 0, trash = 1)))

        deleteDefinitely <- chatsRep.deleteChat(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId)

        optUserChat <- db.run(UserChatsTable.all.filter(uc => uc.chatId === basicTestDB.chatRow.chatId
          && uc.userId === basicTestDB.userRow.userId).result.headOption)
        userChat = optUserChat.value
      } yield assert(
        deleteDefinitely &&
          userChat.inbox === 0 &&
          userChat.sent === 0 &&
          userChat.draft === 0 &&
          userChat.trash === 0)

    }

    "not definitely delete a chat if it is not in trash" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow))

        chatBeforeDeleteTry <- db.run(UserChatsTable.all.filter(uc => uc.chatId === basicTestDB.chatRow.chatId
          && uc.userId === basicTestDB.userRow.userId).result.headOption)
        deleteTry <- chatsRep.deleteChat(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId)
        chatAfterDeleteTry <- db.run(UserChatsTable.all.filter(uc => uc.chatId === basicTestDB.chatRow.chatId
          && uc.userId === basicTestDB.userRow.userId).result.headOption)
      } yield assert(
        !deleteTry &&
          chatBeforeDeleteTry.value.inbox === chatAfterDeleteTry.value.inbox &&
          chatBeforeDeleteTry.value.sent === chatAfterDeleteTry.value.sent &&
          chatBeforeDeleteTry.value.draft === chatAfterDeleteTry.value.draft &&
          chatBeforeDeleteTry.value.trash === chatAfterDeleteTry.value.trash)
    }

    "return false if the user already definitely deleted the chat" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow.copy(inbox = 0)))

        deleteDefinitely <- chatsRep.deleteChat(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId)
        optUserChat <- db.run(UserChatsTable.all.filter(uc => uc.chatId === basicTestDB.chatRow.chatId
          && uc.userId === basicTestDB.userRow.userId).result.headOption)
        userChat = optUserChat.value
      } yield assert(
        !deleteDefinitely &&
          userChat.inbox === 0 &&
          userChat.sent === 0 &&
          userChat.draft === 0 &&
          userChat.trash === 0)
    }

    "return false if the user does not have that chat" in {
      val basicTestDB = genBasicTestDB.sample.value
      val otherAddressRow = genAddressRow.sample.value
      val otherUserRow = genUserRow(otherAddressRow.addressId).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, otherAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow, otherUserRow),
          List(basicTestDB.userChatRow))

        deleteDefinitelyTry <- chatsRep.deleteChat(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId)
      } yield assert(!deleteDefinitelyTry)

    }

    "still allow the user's chat overseers to see the chat" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseeAddressRow = genAddressRow.sample.value
      val overseeUserRow = genUserRow(overseeAddressRow.addressId).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, overseeAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow, overseeUserRow),
          List(
            basicTestDB.userChatRow,
            genUserChatRow(overseeUserRow.userId, basicTestDB.chatRow.chatId).sample.value
              .copy(inbox = 0, trash = 1)),
          oversightRows = List(genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId,
            overseeUserRow.userId).sample.value))

        deleteDefinitely <- chatsRep.deleteChat(basicTestDB.chatRow.chatId, overseeUserRow.userId)

        overseerUserChatAfter <- db.run(UserChatsTable.all.filter(uc => uc.chatId === basicTestDB.chatRow.chatId &&
          uc.userId === basicTestDB.userRow.userId.value).result.headOption)
      } yield assert(
        deleteDefinitely &&
          basicTestDB.userChatRow.inbox === overseerUserChatAfter.value.inbox &&
          basicTestDB.userChatRow.sent === overseerUserChatAfter.value.sent &&
          basicTestDB.userChatRow.draft === overseerUserChatAfter.value.draft &&
          basicTestDB.userChatRow.trash === overseerUserChatAfter.value.trash)
    }
  }

  "SlickChatsRepository#getEmail" should {

    "return a chat with one email for an allowed user" in {
      val basicTestDB = genBasicTestDB.sample.value
      val otherEmailRow = genEmailRow(basicTestDB.chatRow.chatId).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow, otherEmailRow),
          List(
            basicTestDB.emailAddressRow,
            genEmailAddressRow(otherEmailRow.emailId, basicTestDB.chatRow.chatId,
              basicTestDB.addressRow.addressId, From).sample.value))

        optChat <- chatsRep.getEmail(basicTestDB.chatRow.chatId, basicTestDB.emailRow.emailId,
          basicTestDB.userRow.userId)
      } yield optChat mustBe Some(Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(basicTestDB.addressRow.address), Set(),
        Seq(Email(basicTestDB.emailRow.emailId, basicTestDB.addressRow.address, Set(), Set(), Set(),
          basicTestDB.emailRow.body, basicTestDB.emailRow.date, basicTestDB.emailRow.sent, Set()))))

    }

    "return None if the email is a draft of another user" in {
      val basicTestDB = genBasicTestDB.sample.value
      val senderAddressRow = genAddressRow.sample.value
      val senderUserRow = genUserRow(senderAddressRow.addressId).sample.value
      val notSentEmail = genEmailRow(basicTestDB.chatRow.chatId).sample.value.copy(sent = 0)

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, senderAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow, senderUserRow),
          List(basicTestDB.userChatRow, genUserChatRow(senderUserRow.userId, basicTestDB.chatRow.chatId)
            .sample.value.copy(draft = 1)),
          List(basicTestDB.emailRow.copy(sent = 1), notSentEmail),
          List(
            basicTestDB.emailAddressRow.copy(participantType = To),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, From).sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, From)
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, basicTestDB.addressRow.addressId,
              To).sample.value))

        optChat <- chatsRep.getEmail(basicTestDB.chatRow.chatId, notSentEmail.emailId, basicTestDB.userRow.userId)
      } yield optChat mustBe None

    }

    "return None for user that is not allow to see the requested email" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))

        createdChat <- chatsRep.postChat(genCreateChatOption.sample.value, basicTestDB.userRow.userId)

        optChat <- chatsRep.getEmail(createdChat.value.chatId.value, createdChat.value.email.emailId.value,
          genUUID.sample.value)
      } yield optChat mustBe None
    }

    "return None if the requested email exists but is not a part of the specified chat" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))

        firstChat <- chatsRep.postChat(genCreateChatOption.sample.value, basicTestDB.userRow.userId)
        secondChat <- chatsRep.postChat(genCreateChatOption.sample.value, basicTestDB.userRow.userId)

        optChat <- chatsRep.getEmail(firstChat.value.chatId.value, secondChat.value.email.emailId.value,
          basicTestDB.userRow.userId)
      } yield optChat mustBe None
    }
  }

  "SlickChatsRepository#deleteDraft" should {

    "not delete the draft if the user is not the owner/sender of the email" in {
      val basicTestDB = genBasicTestDB.sample.value
      val otherAddressRow = genAddressRow.sample.value
      val otherUserRow = genUserRow(otherAddressRow.addressId).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, otherAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow, otherUserRow),
          List(
            basicTestDB.userChatRow.copy(draft = 1),
            genUserChatRow(otherUserRow.userId, basicTestDB.chatRow.chatId).sample.value.copy(draft = 1)))

        createdDraft <- chatsRep.postEmail(genUpsertEmailOption.sample.value, basicTestDB.chatRow.chatId,
          basicTestDB.userRow.userId)

        deleteDraft <- chatsRep.deleteDraft(basicTestDB.chatRow.chatId, createdDraft.value.email.emailId.value,
          otherUserRow.userId)

        emailRow <- db.run(EmailsTable.all
          .filter(_.emailId === createdDraft.value.email.emailId.value).result.headOption)
        emailAddressesRows <- db.run(EmailAddressesTable.all
          .filter(_.emailId === createdDraft.value.email.emailId.value).result.headOption)
      } yield assert(!deleteDraft && emailRow.nonEmpty && emailAddressesRows.nonEmpty)
    }

    "not delete the email if it is not a draft (i.e. it was already sent)" in {
      val basicTestDB = genBasicTestDB.sample.value
      val origCreateChatDTO = genCreateChatOption.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))
        createdChatDTO <- chatsRep.postChat(origCreateChatDTO.copy(email = origCreateChatDTO.email
          .copy(to = Some(Set(genEmailAddress.sample.value)))), basicTestDB.userRow.userId)
        _ <- chatsRep.patchEmail(
          UpsertEmail(None, None, None, None, None, None, None, Some(true)),
          createdChatDTO.chatId.value, createdChatDTO.email.emailId.value, basicTestDB.userRow.userId)
        _ <- chatsRep.postEmail(genUpsertEmailOption.sample.value, createdChatDTO.chatId.value,
          basicTestDB.userRow.userId)
        _ <- chatsRep.postEmail(genUpsertEmailOption.sample.value, createdChatDTO.chatId.value,
          basicTestDB.userRow.userId)

        numberOfDraftsBefore <- db.run(UserChatsTable.all
          .filter(userChatRow => userChatRow.userId === basicTestDB.userRow.userId &&
            userChatRow.chatId === createdChatDTO.value.chatId.value).map(_.draft)
          .result.headOption)

        deleteDraft <- chatsRep.deleteDraft(createdChatDTO.value.chatId.value, createdChatDTO.value.email.emailId.value,
          basicTestDB.userRow.userId)
        getEmail <- chatsRep.getEmail(createdChatDTO.value.chatId.value, createdChatDTO.value.email.emailId.value,
          basicTestDB.userRow.userId)

        emailRow <- db.run(EmailsTable.all.filter(_.emailId === createdChatDTO.value.email.emailId.value)
          .result.headOption)
        emailAddressesRows <- db.run(EmailAddressesTable.all.
          filter(_.emailId === createdChatDTO.value.email.emailId.value)
          .result.headOption)
        numberOfDraftsAfter <- db.run(UserChatsTable.all
          .filter(userChatRow => userChatRow.userId === basicTestDB.userRow.userId
            && userChatRow.chatId === createdChatDTO.value.chatId.value).map(_.draft)
          .result.headOption)

      } yield assert(!deleteDraft && getEmail.nonEmpty &&
        emailRow.nonEmpty && emailAddressesRows.nonEmpty &&
        numberOfDraftsAfter.value === numberOfDraftsBefore.value)
    }

    "delete a draft (email addresses, attachments and email rows) if the user requesting it is the draft's owner" in {
      val basicTestDB = genBasicTestDB.sample.value
      val chatId = basicTestDB.userChatRow.chatId

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow.copy(draft = 1)),
          List(
            genEmailRow(chatId).sample.value.copy(sent = 1),
            basicTestDB.emailRow.copy(sent = 0)),
          List(basicTestDB.emailAddressRow))

        _ <- db.run(AttachmentsTable.all += AttachmentRow(genUUID.sample.value, basicTestDB.emailRow.emailId))

        numberOfDraftsBefore <- db.run(UserChatsTable.all
          .filter(userChatRow => userChatRow.userId === basicTestDB.userRow.userId &&
            userChatRow.chatId === basicTestDB.chatRow.chatId).map(_.draft)
          .result.headOption)

        deleteDraft <- chatsRep.deleteDraft(basicTestDB.chatRow.chatId, basicTestDB.emailRow.emailId,
          basicTestDB.userRow.userId)
        getEmail <- chatsRep.getEmail(basicTestDB.chatRow.chatId, basicTestDB.emailRow.emailId,
          basicTestDB.userRow.userId)

        emailRow <- db.run(EmailsTable.all.filter(_.emailId === basicTestDB.emailRow.emailId).result.headOption)
        emailAddressesRows <- db.run(EmailAddressesTable.all
          .filter(_.emailId === basicTestDB.emailRow.emailId).result.headOption)
        attachmentsRows <- db.run(AttachmentsTable.all
          .filter(_.emailId === basicTestDB.emailRow.emailId).result.headOption)
        numberOfDraftsAfter <- db.run(UserChatsTable.all
          .filter(userChatRow => userChatRow.userId === basicTestDB.userRow.userId &&
            userChatRow.chatId === basicTestDB.chatRow.chatId).map(_.draft).result.headOption)

      } yield assert(deleteDraft && getEmail.isEmpty &&
        emailRow.isEmpty && emailAddressesRows.isEmpty && attachmentsRows.isEmpty &&
        numberOfDraftsAfter.value === numberOfDraftsBefore.value - 1)
    }

    "delete a draft (email addresses, attachmentsand email rows) if the user requesting it is the draft's owner " +
      "and also delete the chat and userChat rows if the chat was empty after deletion of the draft (only had one draft)" in {
        val basicTestDB = genBasicTestDB.sample.value

        for {
          _ <- fillDB(
            List(basicTestDB.addressRow),
            List(basicTestDB.chatRow),
            List(basicTestDB.userRow),
            List(basicTestDB.userChatRow.copy(draft = 1)),
            List(basicTestDB.emailRow.copy(sent = 0)),
            List(basicTestDB.emailAddressRow))

          _ <- db.run(AttachmentsTable.all += AttachmentRow(genUUID.sample.value, basicTestDB.emailRow.emailId))

          deleteDraft <- chatsRep.deleteDraft(basicTestDB.chatRow.chatId, basicTestDB.emailRow.emailId,
            basicTestDB.userRow.userId)
          getEmail <- chatsRep.getEmail(basicTestDB.chatRow.chatId, basicTestDB.emailRow.emailId,
            basicTestDB.userRow.userId)

          emailRow <- db.run(EmailsTable.all.filter(_.emailId === basicTestDB.emailRow.emailId).result.headOption)
          emailAddressesRows <- db.run(EmailAddressesTable.all
            .filter(_.emailId === basicTestDB.emailRow.emailId).result.headOption)
          attachmentsRows <- db.run(AttachmentsTable.all
            .filter(_.emailId === basicTestDB.emailRow.emailId).result.headOption)
          userChatRow <- db.run(UserChatsTable.all
            .filter(_.chatId === basicTestDB.chatRow.chatId).result.headOption)
          chatRow <- db.run(ChatsTable.all
            .filter(_.chatId === basicTestDB.chatRow.chatId).result.headOption)

        } yield assert(deleteDraft && getEmail.isEmpty &&
          emailRow.isEmpty && emailAddressesRows.isEmpty && attachmentsRows.isEmpty &&
          userChatRow.isEmpty && chatRow.isEmpty)
      }

    "not allow a draft to be patched after it was deleted" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))
        createdDraft <- chatsRep.postChat(genCreateChatOption.sample.value, basicTestDB.userRow.userId)
        deleteDraft <- chatsRep.deleteDraft(createdDraft.chatId.value, createdDraft.email.emailId.value,
          basicTestDB.userRow.userId)

        tryGetEmailBefore <- chatsRep.getEmail(createdDraft.value.chatId.value, createdDraft.value.email.emailId.value,
          basicTestDB.userRow.userId)
        tryPatch <- chatsRep.patchEmail(genUpsertEmailOption.sample.value, createdDraft.chatId.value,
          createdDraft.email.emailId.value, basicTestDB.userRow.userId)
        tryGetEmailAfter <- chatsRep.getEmail(createdDraft.chatId.value, createdDraft.email.emailId.value,
          basicTestDB.userRow.userId)
      } yield assert(tryPatch.isEmpty && tryGetEmailBefore === tryGetEmailAfter && tryGetEmailAfter === None)
    }
  }

  "SlickChatsRepository#postOverseers" should {

    "return None if the chat does not exist" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow))

        optSetOverseer <- chatsRep.postOverseers(genSetPostOverseer.sample.value, genUUID.sample.value,
          basicTestDB.userRow.userId)
      } yield optSetOverseer mustBe None

    }

    "return None if the chat exists but the User does not have access to it" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow))

        optSetOverseer <- chatsRep.postOverseers(genSetPostOverseer.sample.value, basicTestDB.chatRow.chatId,
          basicTestDB.userRow.userId)
      } yield optSetOverseer mustBe None

    }

    "return None if the User has access to the chat but isn't a participant" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow))

        optSetOverseer <- chatsRep.postOverseers(genSetPostOverseer.sample.value, basicTestDB.chatRow.chatId,
          basicTestDB.userRow.userId)
      } yield optSetOverseer mustBe None

    }

    "not return an oversightId if the overseer is not a user" in {
      val basicTestDB = genBasicTestDB.sample.value
      val setPostOverseer = Set(genPostOverseer.sample.value)
      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))

        createdChatDTO <- chatsRep.postChat(genCreateChatOption.sample.value, basicTestDB.userRow.userId)

        optSetOverseer <- chatsRep.postOverseers(setPostOverseer, createdChatDTO.value.chatId.value,
          basicTestDB.userRow.userId)

      } yield optSetOverseer.value mustBe setPostOverseer.map(_.copy(oversightId = None))

    }

    "return the oversightId if it already exists" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseerAddressRow = genAddressRow.sample.value
      val overseerUserRow = genUserRow(overseerAddressRow.addressId).sample.value
      val setPostOverseer = Set(PostOverseer(overseerAddressRow.address, None))
      val oversightRow = genOversightRow(basicTestDB.chatRow.chatId, overseerUserRow.userId,
        basicTestDB.userRow.userId).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, overseerAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow, overseerUserRow),
          List(basicTestDB.userChatRow),
          oversightRows = List(oversightRow))

        _ <- chatsRep.postEmail(genUpsertEmailOption.sample.value, basicTestDB.chatRow.chatId,
          basicTestDB.userRow.userId)

        optSetOverseer <- chatsRep.postOverseers(setPostOverseer, basicTestDB.chatRow.chatId,
          basicTestDB.userRow.userId)

      } yield optSetOverseer.value mustBe setPostOverseer.map(_.copy(oversightId = Some(oversightRow.oversightId)))

    }

    "set the overseer's inbox to 1 if they already have access to the chat" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseerAddressRow = genAddressRow.sample.value
      val overseerUserRow = genUserRow(overseerAddressRow.addressId).sample.value
      val setPostOverseer = Set(PostOverseer(overseerAddressRow.address, None))
      val initCreateChatDTO = genCreateChatOption.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, overseerAddressRow),
          userRows = List(basicTestDB.userRow, overseerUserRow))

        createdChatDTO <- chatsRep.postChat(
          initCreateChatDTO
            .copy(email = initCreateChatDTO.email.copy(to = Some(Set(basicTestDB.addressRow.address)))),
          overseerUserRow.userId)

        _ <- chatsRep.patchEmail(
          UpsertEmail(None, None, None, None, None, None, None, Some(true)),
          createdChatDTO.chatId.value, createdChatDTO.email.emailId.value, overseerUserRow.userId)

        optSetOverseer <- chatsRep.postOverseers(setPostOverseer, createdChatDTO.value.chatId.value,
          basicTestDB.userRow.userId)

        optOversightId <- db.run(OversightsTable.all.filter(_.overseerId === overseerUserRow.userId)
          .map(_.oversightId).result.headOption)

        optOverseerUserChat <- db.run(UserChatsTable.all.filter(_.userId === overseerUserRow.userId).result.headOption)

      } yield assert(
        optSetOverseer.value === setPostOverseer.map(_.copy(oversightId = Some(optOversightId.value))) &&
          optOverseerUserChat.value.inbox === 1 &&
          optOverseerUserChat.value.sent === 1 &&
          optOverseerUserChat.value.draft === 0 &&
          optOverseerUserChat.value.trash === 0)

    }

    "return a new oversightId and create a user-chat for the overseer with inbox = 1" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseerAddressRow = genAddressRow.sample.value
      val overseerUserRow = genUserRow(overseerAddressRow.addressId).sample.value
      val setPostOverseer = Set(PostOverseer(overseerAddressRow.address, None))

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, overseerAddressRow),
          userRows = List(basicTestDB.userRow, overseerUserRow))

        createdChatDTO <- chatsRep.postChat(genCreateChatOption.sample.value, basicTestDB.userRow.userId)

        optSetOverseer <- chatsRep.postOverseers(setPostOverseer, createdChatDTO.value.chatId.value,
          basicTestDB.userRow.userId)

        optOversightId <- db.run(OversightsTable.all.filter(_.overseerId === overseerUserRow.userId)
          .map(_.oversightId).result.headOption)

        optOverseerUserChat <- db.run(UserChatsTable.all.filter(_.userId === overseerUserRow.userId).result.headOption)

      } yield assert(
        optSetOverseer.value === setPostOverseer.map(_.copy(oversightId = Some(optOversightId.value))) &&
          optOverseerUserChat.value.inbox === 1 &&
          optOverseerUserChat.value.sent === 0 &&
          optOverseerUserChat.value.draft === 0 &&
          optOverseerUserChat.value.trash === 0)

    }

    "create new oversights and user-chats for more than one overseer" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseerOneAddressRow = genAddressRow.sample.value
      val overseerOneUserRow = genUserRow(overseerOneAddressRow.addressId).sample.value
      val overseerTwoAddressRow = genAddressRow.sample.value
      val overseerTwoUserRow = genUserRow(overseerTwoAddressRow.addressId).sample.value
      val setPostOverseer = Set(
        PostOverseer(overseerOneAddressRow.address, None),
        PostOverseer(overseerTwoAddressRow.address, None))

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, overseerOneAddressRow, overseerTwoAddressRow),
          userRows = List(basicTestDB.userRow, overseerOneUserRow, overseerTwoUserRow))

        createdChatDTO <- chatsRep.postChat(genCreateChatOption.sample.value, basicTestDB.userRow.userId)

        optSetOverseer <- chatsRep.postOverseers(setPostOverseer, createdChatDTO.value.chatId.value,
          basicTestDB.userRow.userId)

        overseersIds <- db.run(OversightsTable.all.filter(_.overseeId === basicTestDB.userRow.userId)
          .map(_.overseerId).result)

        optOverseerOneUserChat <- db.run(UserChatsTable.all.filter(_.userId === overseerOneUserRow.userId)
          .result.headOption)

        optOverseerTwoUserChat <- db.run(UserChatsTable.all.filter(_.userId === overseerTwoUserRow.userId)
          .result.headOption)

      } yield assert(
        overseersIds.contains(overseerOneUserRow.userId) &&
          overseersIds.contains(overseerTwoUserRow.userId) &&
          optOverseerOneUserChat.value.inbox === 1 &&
          optOverseerTwoUserChat.value.inbox === 1)

    }

  }

  "SlickChatsRepository#getOverseers" should {

    "return None if the chat does not exist" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow))

        optSetOverseer <- chatsRep.getOverseers(genUUID.sample.value, basicTestDB.userRow.userId)
      } yield optSetOverseer mustBe None

    }

    "return None if the chat exists but the User does not have access to it" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow))

        optSetOverseer <- chatsRep.getOverseers(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId)
      } yield optSetOverseer mustBe None

    }

    "return the user's overseers" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseerOneAddressRow = genAddressRow.sample.value
      val overseerOneUserRow = genUserRow(overseerOneAddressRow.addressId).sample.value
      val overseerTwoAddressRow = genAddressRow.sample.value
      val overseerTwoUserRow = genUserRow(overseerTwoAddressRow.addressId).sample.value
      val setPostOverseer = Set(
        PostOverseer(overseerOneAddressRow.address, None),
        PostOverseer(overseerTwoAddressRow.address, None))

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, overseerOneAddressRow, overseerTwoAddressRow),
          userRows = List(basicTestDB.userRow, overseerOneUserRow, overseerTwoUserRow))

        createdChatDTO <- chatsRep.postChat(genCreateChatOption.sample.value, basicTestDB.userRow.userId)

        postedOverseers <- chatsRep.postOverseers(setPostOverseer, createdChatDTO.chatId.value,
          basicTestDB.userRow.userId)

        getOverseers <- chatsRep.getOverseers(createdChatDTO.chatId.value, basicTestDB.userRow.userId)

      } yield getOverseers mustBe postedOverseers

    }

  }

  "SlickChatsRepository#deleteOverseers" should {

    "return false if the chat does not exist" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow))

        result <- chatsRep.deleteOverseer(genUUID.sample.value, genUUID.sample.value,
          basicTestDB.userRow.userId)
      } yield assert(!result)

    }

    "return false if the chat exists but the User does not have access to it" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow))

        result <- chatsRep.deleteOverseer(genUUID.sample.value, genUUID.sample.value,
          basicTestDB.userRow.userId)
      } yield assert(!result)

    }

    "return false if the oversightId is incorrect" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseerAddressRow = genAddressRow.sample.value
      val overseerUserRow = genUserRow(overseerAddressRow.addressId).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, overseerAddressRow),
          userRows = List(basicTestDB.userRow, overseerUserRow))

        createdChatDTO <- chatsRep.postChat(genCreateChatOption.sample.value, basicTestDB.userRow.userId)

        postedOverseers <- chatsRep.postOverseers(
          Set(PostOverseer(overseerAddressRow.address, None)),
          createdChatDTO.chatId.value,
          basicTestDB.userRow.userId)

        result <- chatsRep.deleteOverseer(createdChatDTO.chatId.value, genUUID.sample.value,
          basicTestDB.userRow.userId)

      } yield assert(!result)

    }

    "return false if the user is not the oversee" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseerAddressRow = genAddressRow.sample.value
      val overseerUserRow = genUserRow(overseerAddressRow.addressId).sample.value
      val overseeAddressRow = genAddressRow.sample.value
      val overseeUserRow = genUserRow(overseeAddressRow.addressId).sample.value
      val oversightRow = genOversightRow(basicTestDB.chatRow.chatId, overseerUserRow.userId, overseeUserRow.userId)
        .sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, overseerAddressRow, overseeAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow, overseerUserRow, overseeUserRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow),
          List(basicTestDB.emailAddressRow),
          List(oversightRow))

        result <- chatsRep.deleteOverseer(basicTestDB.chatRow.chatId, oversightRow.oversightId,
          basicTestDB.userRow.userId)

      } yield assert(!result)

    }

    "delete a valid overseer" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseerAddressRow = genAddressRow.sample.value
      val overseerUserRow = genUserRow(overseerAddressRow.addressId).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, overseerAddressRow),
          userRows = List(basicTestDB.userRow, overseerUserRow))

        createdChatDTO <- chatsRep.postChat(genCreateChatOption.sample.value, basicTestDB.userRow.userId)

        postedOverseers <- chatsRep.postOverseers(
          Set(PostOverseer(overseerAddressRow.address, None)),
          createdChatDTO.chatId.value,
          basicTestDB.userRow.userId)

        oversightId = postedOverseers.value.headOption.value.oversightId.value

        preDeletionOversight <- db.run(OversightsTable.all.filter(_.oversightId === oversightId)
          .map(_.oversightId).result.headOption)

        result <- chatsRep.deleteOverseer(
          createdChatDTO.chatId.value,
          oversightId,
          basicTestDB.userRow.userId)

        postDeletionOversight <- db.run(OversightsTable.all.filter(_.oversightId === oversightId).result.headOption)

      } yield assert(result &&
        preDeletionOversight === Some(oversightId) &&
        postDeletionOversight === None)

    }

  }

}

case class BasicTestDB(addressRow: AddressRow, userRow: UserRow, chatRow: ChatRow, emailRow: EmailRow,
  emailAddressRow: EmailAddressRow, userChatRow: UserChatRow)

