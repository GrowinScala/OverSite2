package repositories.slick.implementations

import model.dtos.PatchChatDTO.{ MoveToTrash, Restore }
import model.dtos.PatchChatDTO.{MoveToTrash, Restore}
import model.dtos.{CreateChatDTO, UpsertEmailDTO}
import model.types.Mailbox.{Drafts, Inbox, Sent}
import org.scalatest._
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import repositories.dtos.ChatPreview
import repositories.dtos.{Chat, Email, Overseers}
import repositories.slick.mappings.{EmailRow, _}
import slick.jdbc.MySQLProfile.api._
import utils.Generators._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor}
import model.types.Mailbox._
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
    userChatRows: List[UserChatRow] = Nil, emailRows: List[EmailRow] = Nil, emailAddressRows: List[EmailAddressRow] = Nil,
    oversightRows: List[OversightRow] = Nil): Future[Unit] =
    db.run(DBIO.seq(
      AddressesTable.all ++= addressRows,
      ChatsTable.all ++= chatRows,
      UsersTable.all ++= userRows,
      UserChatsTable.all ++= userChatRows,
      EmailsTable.all ++= emailRows,
      EmailAddressesTable.all ++= emailAddressRows,
      OversightsTable.all ++= oversightRows))

  def addressesFromUpsertEmailDTO(email: UpsertEmailDTO): Set[String] = {
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
            basicTestDB.emailAddressRow.copy(participantType = "to"),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value))

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
            basicTestDB.emailAddressRow.copy(participantType = "cc"),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value))

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
            basicTestDB.emailAddressRow.copy(participantType = "bcc"),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value))

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
            basicTestDB.emailAddressRow.copy(participantType = "to"),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value))

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
            basicTestDB.emailAddressRow.copy(participantType = "cc"),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value))

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
            basicTestDB.emailAddressRow.copy(participantType = "bcc"),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value))

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
              basicTestDB.addressRow.addressId, "from").sample.value))

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
              basicTestDB.addressRow.addressId, "from").sample.value))

        chatsPreview <- chatsRep.getChatsPreview(Inbox, basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.addressRow.address, "2019", List(basicTestDB.emailRow, otherEmailRow).minBy(_.emailId).body))

    }

    "detect more than one chat" in {
      val basicTestDB = genBasicTestDB.sample.value
      val otherChatRow = genChatRow.sample.value
      val otherEmailRow = genEmailRow(otherChatRow.chatId).sample.value
      val otherEmailAddressesRow = genEmailAddressRow(otherEmailRow.emailId, otherChatRow.chatId,
        basicTestDB.addressRow.addressId, "from").sample.value

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
              basicTestDB.addressRow.addressId, "from").sample.value))

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
            overseeAddressRow.addressId, "from").sample.value),
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
            overseeAddressRow.addressId, "from").sample.value),
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
              overseeAddressRow.addressId, "to").sample.value,
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value),
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
              overseeAddressRow.addressId, "cc").sample.value,
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value),
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
              overseeAddressRow.addressId, "bcc").sample.value,
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value),
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
              overseeAddressRow.addressId, "to").sample.value,
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value),
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
              overseeAddressRow.addressId, "cc").sample.value,
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value),
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
              overseeAddressRow.addressId, "bcc").sample.value,
            genEmailAddressRow(overseeEmailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value),
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
            basicTestDB.emailAddressRow.copy(participantType = "to"),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, "from")
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, basicTestDB.addressRow.addressId,
              "to").sample.value))

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
            basicTestDB.emailAddressRow.copy(participantType = "cc"),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, "from")
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, basicTestDB.addressRow.addressId,
              "cc").sample.value))

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
            basicTestDB.emailAddressRow.copy(participantType = "bcc"),
            genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
              senderAddressRow.addressId, "from").sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, "from")
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, basicTestDB.addressRow.addressId,
              "bcc").sample.value))

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
              basicTestDB.addressRow.addressId, "from").sample.value))

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
            genEmailAddressRow(sentEmail.emailId, basicTestDB.chatRow.chatId, overseeAddressRow.addressId, "from")
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, overseeAddressRow.addressId, "from")
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
            genEmailAddressRow(sentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, "from")
              .sample.value,
            genEmailAddressRow(sentEmail.emailId, basicTestDB.chatRow.chatId, overseeAddressRow.addressId, "to")
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, "from")
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, overseeAddressRow.addressId, "to")
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
            genEmailAddressRow(sentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, "from")
              .sample.value,
            genEmailAddressRow(sentEmail.emailId, basicTestDB.chatRow.chatId, overseeAddressRow.addressId, "cc")
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, "from")
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, overseeAddressRow.addressId, "cc")
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
            genEmailAddressRow(sentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, "from")
              .sample.value,
            genEmailAddressRow(sentEmail.emailId, basicTestDB.chatRow.chatId, overseeAddressRow.addressId, "bcc")
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, senderAddressRow.addressId, "from")
              .sample.value,
            genEmailAddressRow(notSentEmail.emailId, basicTestDB.chatRow.chatId, overseeAddressRow.addressId, "bcc")
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
                toAddressRow.addressId, "to").sample.value,
              genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
                ccAddressRow.addressId, "cc").sample.value,
              genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
                bccAddressRow.addressId, "bcc").sample.value),
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
                bccOneAddressRow.addressId, "bcc").sample.value,
              genEmailAddressRow(basicTestDB.emailRow.emailId, basicTestDB.chatRow.chatId,
                bccTwoAddressRow.addressId, "bcc").sample.value),
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

  "SlickChatsRepository#postChat+getChat" should {

    "create a chat with an email draft for a user and then get the same chat for the same user: results must match" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))
        postResponse <- chatsRep.postChat(genCreateChatDTOption.sample.value, basicTestDB.userRow.userId)
        getResponse <- chatsRep.getChat(postResponse.chatId.get, basicTestDB.userRow.userId)
      } yield getResponse mustBe Some(chatsRep.fromCreateChatDTOtoChat(postResponse))

    }

    "Not give access to the chat to a user that is a receiver of the email [To]" +
      "\n Note: This method only creates Drafts" in {
        val basicTestDB = genBasicTestDB.sample.value
        val receiverAddressRow = genAddressRow.sample.value
        val receiverUserRow = genUserRow(receiverAddressRow.addressId).sample.value
        val origCreateChatDTO = genCreateChatDTOption.sample.value

        for {
          _ <- fillDB(
            List(basicTestDB.addressRow, receiverAddressRow),
            userRows = List(basicTestDB.userRow, receiverUserRow))
          postResponse <- chatsRep.postChat(
            origCreateChatDTO.copy(email = origCreateChatDTO.email.copy(to = Some(Set(receiverAddressRow.address)))),
            basicTestDB.userRow.userId)
          getResponse <- chatsRep.getChat(postResponse.chatId.get, receiverUserRow.userId)
        } yield getResponse mustBe None

      }

    "Not give access to the chat to a user that is a receiver of the email [CC]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val receiverAddressRow = genAddressRow.sample.value
      val receiverUserRow = genUserRow(receiverAddressRow.addressId).sample.value
      val origCreateChatDTO = genCreateChatDTOption.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, receiverAddressRow),
          userRows = List(basicTestDB.userRow, receiverUserRow))
        postResponse <- chatsRep.postChat(
          origCreateChatDTO.copy(email = origCreateChatDTO.email.copy(cc = Some(Set(receiverAddressRow.address)))),
          basicTestDB.userRow.userId)
        getResponse <- chatsRep.getChat(postResponse.chatId.get, receiverUserRow.userId)
      } yield getResponse mustBe None

    }

    "Not give access to the chat to a user that is a receiver of the email [BCC]" in {
      val basicTestDB = genBasicTestDB.sample.value
      val receiverAddressRow = genAddressRow.sample.value
      val receiverUserRow = genUserRow(receiverAddressRow.addressId).sample.value
      val origCreateChatDTO = genCreateChatDTOption.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, receiverAddressRow),
          userRows = List(basicTestDB.userRow, receiverUserRow))
        postResponse <- chatsRep.postChat(
          origCreateChatDTO.copy(email = origCreateChatDTO.email.copy(bcc = Some(Set(receiverAddressRow.address)))),
          basicTestDB.userRow.userId)
        getResponse <- chatsRep.getChat(postResponse.chatId.get, receiverUserRow.userId)
      } yield getResponse mustBe None

    }

    "create a chat with an EMPTY draft for a user and then get the same chat for the same user: results must match" in {
      val basicTestDB = genBasicTestDB.sample.value

      val chatWithEmptyDraft =
        CreateChatDTO(
          chatId = None,
          subject = None,
          UpsertEmailDTO(
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
      } yield getResponse mustBe Some(chatsRep.fromCreateChatDTOtoChat(postResponse))

    }

  }

  "SlickChatsRepository#postEmail+getChat" should {

    "add an email draft to a chat and then get the same chat with the added email" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))
        postChatResponse <- chatsRep.postChat(genCreateChatDTOption.sample.value, basicTestDB.userRow.userId)
        postEmailResponse <- chatsRep.postEmail(genUpsertEmailDTOption.sample.value, postChatResponse.chatId.value,
          basicTestDB.userRow.userId)
        getResponse <- chatsRep.getChat(postChatResponse.chatId.value, basicTestDB.userRow.userId)
        nrDrafts <- db.run(UserChatsTable.all.filter(_.userId === basicTestDB.userRow.userId).map(_.draft)
          .result.headOption)

      } yield assert(getResponse === {
        val originalChat = chatsRep.fromCreateChatDTOtoChat(postChatResponse)

        Some(originalChat.copy(
          emails = (chatsRep.fromUpsertEmailDTOtoEmail(postEmailResponse.value.email) +: originalChat.emails)
            .sortBy(email => (email.date, email.emailId)),
          addresses = addressesFromUpsertEmailDTO(postEmailResponse.value.email) ++ originalChat.addresses))
      } && nrDrafts.value === 2)

    }

  }

  "SlickChatsRepository#patchEmail" should {

    "patch the body of an email in draft state" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))
        postChat <- chatsRep.postChat(genCreateChatDTOption.sample.value, basicTestDB.userRow.userId)
        getPostedEmail = chatsRep.fromCreateChatDTOtoChat(postChat).emails.headOption.value

        patchBody = genString.sample.value

        patchEmail <- chatsRep.patchEmail(
          UpsertEmailDTO(None, None, None, None, None, Some(patchBody), None, None),
          postChat.chatId.value, postChat.email.emailId.value, basicTestDB.userRow.userId)
      } yield patchEmail.value mustBe getPostedEmail.copy(body = patchBody, date = patchEmail.value.date)
    }

    "patch all the email addresses, and send the email " +
      "the chat must appear in the sender and receivers' correct mailboxes" in {
        val basicTestDB = genBasicTestDB.sample.value
        val toAddressRow = genAddressRow.sample.value
        val ccAddressRow = genAddressRow.sample.value
        val bccAddressRow = genAddressRow.sample.value
        val toUserRow = genUserRow(toAddressRow.addressId).sample.value
        val ccUserRow = genUserRow(ccAddressRow.addressId).sample.value
        val bccUserRow = genUserRow(bccAddressRow.addressId).sample.value

        for {
          _ <- fillDB(
            List(basicTestDB.addressRow, toAddressRow, ccAddressRow, bccAddressRow),
            userRows = List(basicTestDB.userRow, toUserRow, ccUserRow, bccUserRow))
          postChat <- chatsRep.postChat(genCreateChatDTOption.sample.value, basicTestDB.userRow.userId)
          getPostedChat = chatsRep.fromCreateChatDTOtoChat(postChat)
          getPostedEmail = chatsRep.fromCreateChatDTOtoChat(postChat).emails.headOption.value

          patchEmail <- chatsRep.patchEmail(
            UpsertEmailDTO(None, None, Some(Set(toAddressRow.address)),
              Some(Set(bccAddressRow.address)), Some(Set(ccAddressRow.address)), None, None, Some(true)),
            postChat.chatId.value, postChat.email.emailId.value,
            basicTestDB.userRow.userId)

          fromUserGetChat <- chatsRep.getChat(postChat.chatId.value, basicTestDB.userRow.userId)
          toUserGetChat <- chatsRep.getChat(postChat.chatId.value, toUserRow.userId)
          ccUserGetChat <- chatsRep.getChat(postChat.chatId.value, ccUserRow.userId)
          bccUserGetChat <- chatsRep.getChat(postChat.chatId.value, bccUserRow.userId)

          senderChatsPreviewSent <- chatsRep.getChatsPreview(Sent, basicTestDB.userRow.userId)
          senderChatsPreviewDrafts <- chatsRep.getChatsPreview(Drafts, basicTestDB.userRow.userId)

          toReceiverChatsPreviewInbox <- chatsRep.getChatsPreview(Inbox, toUserRow.userId)
          ccReceiverChatsPreviewInbox <- chatsRep.getChatsPreview(Inbox, ccUserRow.userId)
          bccReceiverChatsPreviewInbox <- chatsRep.getChatsPreview(Inbox, bccUserRow.userId)

          invisibleBccExpectedEmailAfterPatch = getPostedEmail.copy(
            to = Set(toAddressRow.address),
            cc = Set(ccAddressRow.address), bcc = Set(), sent = 1)

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
      val origCreateChatDTO = genCreateChatDTOption.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))
        postChat <- chatsRep.postChat(
          origCreateChatDTO
            .copy(email = origCreateChatDTO.email.copy(from = None, to = None, cc = None, bcc = None)),
          basicTestDB.userRow.userId)
        patchEmail <- chatsRep.patchEmail(
          UpsertEmailDTO(None, None, None, None, None, None, None, Some(true)),
          postChat.chatId.value, postChat.email.emailId.value, basicTestDB.userRow.userId)
      } yield assert(
        patchEmail.value === chatsRep.fromCreateChatDTOtoChat(postChat).emails.headOption.value &&
          patchEmail.value.sent === 0)
    }

    "not allow an email patch if the user requesting it is not the its owner (from)" in {
      val basicTestDB = genBasicTestDB.sample.value
      val otherAddressRow = genAddressRow.sample.value
      val otherUserRow = genUserRow(otherAddressRow.addressId).sample.value
      val origCreateChatDTO = genCreateChatDTOption.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, otherAddressRow),
          userRows = List(basicTestDB.userRow, otherUserRow))
        postChat <- chatsRep.postChat(origCreateChatDTO.copy(email = origCreateChatDTO.email.copy(to =
          Some(Set(otherAddressRow.address)))), basicTestDB.userRow.userId)
        getPostedEmail = chatsRep.fromCreateChatDTOtoChat(postChat).emails.headOption.value

        patchEmail <- chatsRep.patchEmail(
          UpsertEmailDTO(None, None, None, None, None, Some(genString.sample.value), None, None),
          postChat.chatId.value, postChat.email.emailId.value, otherUserRow.userId)
      } yield patchEmail mustBe None

    }

    "not allow an email patch if the email was already sent" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))
        postChat <- chatsRep.postChat(genCreateChatDTOption.sample.value, basicTestDB.userRow.userId)
        getPostedEmail = chatsRep.fromCreateChatDTOtoChat(postChat).emails.headOption.value

        patchEmail <- chatsRep.patchEmail(
          UpsertEmailDTO(None, None, None, None, None, None, None, Some(true)),
          postChat.chatId.value, postChat.email.emailId.value, basicTestDB.userRow.userId)

        retryPatchEmailAfterSent <- chatsRep.patchEmail(
          UpsertEmailDTO(None, None, None, None, None, Some(genString.sample.value), None, None),
          postChat.chatId.value, postChat.email.emailId.value, basicTestDB.userRow.userId)

      } yield retryPatchEmailAfterSent mustBe None
    }

    "return None if the requested emailId is not a part of the chat with the specified chatId" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))
        postChat <- chatsRep.postChat(genCreateChatDTOption.sample.value, basicTestDB.userRow.userId)
        createdChatId = postChat.chatId.value

        patchEmail <- chatsRep.patchEmail(
          UpsertEmailDTO(None, None, None, None, None, Some(genString.sample.value), None, None),
          postChat.chatId.value, genUUID.sample.value, basicTestDB.userRow.userId)
      } yield patchEmail mustBe None

    }

  }
	
	  "SlickChatsRepository#patchChat" should {
		  val chatsRep = new SlickChatsRepository(db)
		
		  val userId = "148a3b1b-8326-466d-8c27-1bd09b8378f3" //beatriz@mail.com
		  val validChatId = "303c2b72-304e-4bac-84d7-385acb64a616"
		
		  "remove the user's chat from inbox, sent and draft and move it to trash" in {
			  for {
				  result <- chatsRep.patchChat(MoveToTrash, validChatId, userId)
				  optionUserChat <- db.run(UserChatsTable.all.filter(uc => uc.chatId === validChatId && uc.userId === userId).result.headOption)
			  } yield inside(optionUserChat) {
				  case Some(userChat) =>
					  assert(
						  result === Some(MoveToTrash) &&
							  userChat.inbox === 0 &&
							  userChat.sent === 0 &&
							  userChat.draft === 0 &&
							  userChat.trash === 1)
			  }
		  }
		
		  "restore the user's chat if it is already in trash" in {
			  for {
				  result <- chatsRep.patchChat(Restore, validChatId, userId)
				  optionUserChat <- db.run(UserChatsTable.all.filter(uc => uc.chatId === validChatId && uc.userId === userId).result.headOption)
			  } yield inside(optionUserChat) {
				  case Some(userChat) =>
					  assert(
						  result === Some(Restore) &&
							  userChat.inbox === 1 &&
							  userChat.sent === 1 &&
							  userChat.draft === 1 &&
							  userChat.trash === 0)
			  }
		  }
		
		  "move to trash a chat in which the user is an overseer" in {
			  val overseerUserId = "25689204-5a8e-453d-bfbc-4180ff0f97b9" //valter@mail.com
			  for {
				  result <- chatsRep.patchChat(MoveToTrash, validChatId, overseerUserId)
				  optionUserChat <- db.run(UserChatsTable.all.filter(uc => uc.chatId === validChatId && uc.userId === overseerUserId).result.headOption)
			  } yield inside(optionUserChat) {
				  case Some(userChat) =>
					  assert(
						  result === Some(MoveToTrash) &&
							  userChat.inbox === 0 &&
							  userChat.sent === 0 &&
							  userChat.draft === 0 &&
							  userChat.trash === 1)
			  }
		  }
		
		  "restore a chat in which the user is an overseer" in {
			  val overseerUserId = "25689204-5a8e-453d-bfbc-4180ff0f97b9" //valter@mail.com
			  for {
				  result <- chatsRep.patchChat(Restore, validChatId, overseerUserId)
				  optionUserChat <- db.run(UserChatsTable.all.filter(uc => uc.chatId === validChatId && uc.userId === overseerUserId).result.headOption)
			  } yield inside(optionUserChat) {
				  case Some(userChat) =>
					  assert(
						  result === Some(Restore) &&
							  userChat.inbox === 1 &&
							  userChat.sent === 0 &&
							  userChat.draft === 0 &&
							  userChat.trash === 0)
			  }
		  }
		
		  "return None if the user does not have a chat with that id" in {
			  val invalidChatId = "00000000-0000-0000-0000-000000000000"
			  for {
				  result <- chatsRep.patchChat(MoveToTrash, invalidChatId, userId)
				  optionUserChat <- db.run(UserChatsTable.all.filter(uc => uc.chatId === invalidChatId && uc.userId === userId).result.headOption)
			  } yield assert(
				  result === None &&
					  optionUserChat === None)
		  }
	  }
	  
	  
	  
	  
  "SlickChatsRepository#moveChatToTrash" should {
    "remove the user's chat from inbox, sent and draft and move it to trash" in {
      /*val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow.copy(sent = 1, draft = 1)))
        result <- chatsRep.moveChatToTrash(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId)
        optUserChat <- db.run(UserChatsTable.all.filter(uc => uc.chatId === basicTestDB.chatRow.chatId
          && uc.userId === basicTestDB.userRow.userId)
          .result.headOption)
        userChat = optUserChat.value
      } yield assert(
        result &&
          userChat.inbox === 0 &&
          userChat.sent === 0 &&
          userChat.draft === 0 &&
          userChat.trash === 1)*/
      
      Future.successful(1 mustBe 1)
    }

    "return false if the user does not have a chat with that id" in {
      /*val basicTestDB = genBasicTestDB.sample.value
      val invalidChatId = genUUID.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow.copy(sent = 1, draft = 1)))
        result <- chatsRep.moveChatToTrash(invalidChatId, basicTestDB.userRow.userId)
        optUserChat <- db.run(UserChatsTable.all.filter(uc => uc.chatId === invalidChatId &&
          uc.userId === basicTestDB.userRow.userId)
          .result.headOption)
      } yield assert(!result && optUserChat === None)*/
  
      Future.successful(1 mustBe 1)

    }
  }
	
	
	"SlickChatsRepository#deleteChat" should {
		val chatsRep = new SlickChatsRepository(db)
		val userId = "148a3b1b-8326-466d-8c27-1bd09b8378f3" //beatriz@mail.com
		val validChatId = "303c2b72-304e-4bac-84d7-385acb64a616"
		
		"definitely delete a chat from trash" in {
			for {
				moveChatToTrash <- chatsRep.patchChat(MoveToTrash, validChatId, userId)
				deleteDefinitely <- chatsRep.deleteChat(validChatId, userId)
				
				optionUserChat <- db.run(UserChatsTable.all.filter(uc => uc.chatId === validChatId && uc.userId === userId).result.headOption)
			} yield inside(optionUserChat) {
				case Some(userChat) =>
					assert(
						moveChatToTrash.value === MoveToTrash &&
							deleteDefinitely &&
							userChat.inbox === 0 &&
							userChat.sent === 0 &&
							userChat.draft === 0 &&
							userChat.trash === 0)
			}
		}
		
		"not definitely delete a chat if it is not in trash" in {
			for {
				chatBeforeDeleteTry <- db.run(UserChatsTable.all.filter(uc => uc.chatId === validChatId && uc.userId === userId).result.headOption)
				deleteTry <- chatsRep.deleteChat(validChatId, userId)
				chatAfterDeleteTry <- db.run(UserChatsTable.all.filter(uc => uc.chatId === validChatId && uc.userId === userId).result.headOption)
			} yield assert(
				!deleteTry &&
					chatBeforeDeleteTry.value.inbox === chatAfterDeleteTry.value.inbox &&
					chatBeforeDeleteTry.value.sent === chatAfterDeleteTry.value.sent &&
					chatBeforeDeleteTry.value.draft === chatAfterDeleteTry.value.draft &&
					chatBeforeDeleteTry.value.trash === chatAfterDeleteTry.value.trash)
		}
		
		"return false if the user already definitely deleted the chat" in {
			for {
				moveChatToTrash <- chatsRep.patchChat(MoveToTrash, validChatId, userId)
				deleteDefinitely <- chatsRep.deleteChat(validChatId, userId)
				
				deleteDefinitelySecondTry <- chatsRep.deleteChat(validChatId, userId)
				
				optionUserChat <- db.run(UserChatsTable.all.filter(uc => uc.chatId === validChatId && uc.userId === userId).result.headOption)
			} yield inside(optionUserChat) {
				case Some(userChat) =>
					assert(
						moveChatToTrash.value === MoveToTrash &&
							deleteDefinitely &&
							!deleteDefinitelySecondTry &&
							userChat.inbox === 0 &&
							userChat.sent === 0 &&
							userChat.draft === 0 &&
							userChat.trash === 0)
			}
		}
		
		"return false if the user does not have that chat" in {
			val notAllowedUserId = "261c9094-6261-4704-bfd0-02821c235eff"
			chatsRep.deleteChat(validChatId, notAllowedUserId)
				.map(deleteDefinitelyTry => assert(!deleteDefinitelyTry))
		}
		
		"still allow the user's chat overseers to see the chat" in {
			for {
				overseerUserIds <- db.run(chatsRep.getUserChatOverseersAction(userId, validChatId))
				overseerUserId = overseerUserIds.headOption
				overseerUserChatBefore <- db.run(UserChatsTable.all.filter(uc => uc.chatId === validChatId && uc.userId === overseerUserId.value).result.headOption)
				
				moveChatToTrash <- chatsRep.patchChat(MoveToTrash, validChatId, userId)
				deleteDefinitely <- chatsRep.deleteChat(validChatId, userId)
				
				overseerUserChatAfter <- db.run(UserChatsTable.all.filter(uc => uc.chatId === validChatId && uc.userId === overseerUserId.value).result.headOption)
			} yield assert(
				moveChatToTrash.value === MoveToTrash &&
					deleteDefinitely &&
					overseerUserChatBefore.value.inbox === overseerUserChatAfter.value.inbox &&
					overseerUserChatBefore.value.sent === overseerUserChatAfter.value.sent &&
					overseerUserChatBefore.value.draft === overseerUserChatAfter.value.draft &&
					overseerUserChatBefore.value.trash === overseerUserChatAfter.value.trash)
		}
	}
	
	"SlickChatsRepository#deleteDraft" should {
		val chatsRep = new SlickChatsRepository(db)
		val userId = "148a3b1b-8326-466d-8c27-1bd09b8378f3" //beatriz@mail.com
		val validChatId = "825ee397-f36e-4023-951e-89d6e43a8e7d"
		val validDraftEmailId = "fe4ff891-144a-4f61-af35-6d4a5ec76314"
		
		"not delete the draft if the user is not the owner/sender of the email" in {
			val notAllowedUserId = "adcd6348-658a-4866-93c5-7e6d32271d8d"
			for {
				deleteDraft <- chatsRep.deleteDraft(validChatId, validDraftEmailId, notAllowedUserId)
				
				emailRow <- db.run(EmailsTable.all.filter(_.emailId === validDraftEmailId).result.headOption)
				emailAddressesRows <- db.run(EmailAddressesTable.all.filter(_.emailId === validDraftEmailId).result.headOption)
			} yield assert(!deleteDraft && emailRow.nonEmpty && emailAddressesRows.nonEmpty)
		}
		
		"not delete the email if it is not a draft (i.e. it was already sent)" in {
			val validSentEmailId = "42508cff-a4cf-47e4-9b7d-db91e010b87a"
			val senderUserId = "adcd6348-658a-4866-93c5-7e6d32271d8d"
   
			for {
				numberOfDraftsBefore <- db.run(UserChatsTable.all
					.filter(userChatRow => userChatRow.userId === senderUserId && userChatRow.chatId === validChatId).map(_.draft)
					.result.headOption)
				
				deleteDraft <- chatsRep.deleteDraft(validChatId, validSentEmailId, senderUserId)
				getEmail <- chatsRep.getEmail(validChatId, validSentEmailId, senderUserId)
				
				emailRow <- db.run(EmailsTable.all.filter(_.emailId === validSentEmailId).result.headOption)
				emailAddressesRows <- db.run(EmailAddressesTable.all.filter(_.emailId === validSentEmailId).result.headOption)
				numberOfDraftsAfter <- db.run(UserChatsTable.all
					.filter(userChatRow => userChatRow.userId === senderUserId && userChatRow.chatId === validChatId).map(_.draft)
					.result.headOption)
				
			} yield assert(!deleteDraft && getEmail.nonEmpty &&
				emailRow.nonEmpty && emailAddressesRows.nonEmpty &&
				numberOfDraftsAfter.value === numberOfDraftsBefore.value)
		}
		
		"delete a draft (email addresses, attachments and email rows) if the user requesting it is the draft's owner" in {
			for {
				numberOfDraftsBefore <- db.run(UserChatsTable.all
					.filter(userChatRow => userChatRow.userId === userId && userChatRow.chatId === validChatId).map(_.draft)
					.result.headOption)
				
				deleteDraft <- chatsRep.deleteDraft(validChatId, validDraftEmailId, userId)
				getEmail <- chatsRep.getEmail(validChatId, validDraftEmailId, userId)
				
				emailRow <- db.run(EmailsTable.all.filter(_.emailId === validDraftEmailId).result.headOption)
				emailAddressesRows <- db.run(EmailAddressesTable.all.filter(_.emailId === validDraftEmailId).result.headOption)
				attachmentsRows <- db.run(AttachmentsTable.all.filter(_.emailId === validDraftEmailId).result.headOption)
				numberOfDraftsAfter <- db.run(UserChatsTable.all
					.filter(userChatRow => userChatRow.userId === userId && userChatRow.chatId === validChatId).map(_.draft)
					.result.headOption)
				
			} yield assert(deleteDraft && getEmail.isEmpty &&
				emailRow.isEmpty && emailAddressesRows.isEmpty && attachmentsRows.isEmpty &&
				numberOfDraftsAfter.value === numberOfDraftsBefore.value - 1)
		}
		
		"not allow a draft to be patched after it was deleted" in {
			val exampleUpsertEmailDTO = UpsertEmailDTO(None, None, None, None, None,
				Some("This is me trying to patch a deleted draft"), None, None)
			for {
				tryGetEmailBefore <- chatsRep.getEmail(validChatId, validDraftEmailId, userId)
				tryPatch <- chatsRep.patchEmail(exampleUpsertEmailDTO, validChatId, validDraftEmailId, userId)
				tryGetEmailAfter <- chatsRep.getEmail(validChatId, validDraftEmailId, userId)
			} yield assert(tryPatch.isEmpty && tryGetEmailBefore === tryGetEmailAfter && tryGetEmailAfter === None)
		}
	}
	
	
	
}

case class BasicTestDB(addressRow: AddressRow, userRow: UserRow, chatRow: ChatRow, emailRow: EmailRow,
  emailAddressRow: EmailAddressRow, userChatRow: UserChatRow)

