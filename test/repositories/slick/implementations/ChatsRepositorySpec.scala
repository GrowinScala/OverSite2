package repositories.slick.implementations

import java.math.RoundingMode

import com.google.common.math.IntMath.divide
import repositories.dtos.PatchChat.{ ChangeSubject, MoveToTrash, Restore }
import model.types.Mailbox.{ Drafts, Inbox, Sent }
import org.scalatest._
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import repositories.dtos._
import repositories.slick.mappings._
import slick.jdbc.MySQLProfile.api._
import org.scalacheck.Gen._

import scala.math._
import scala.concurrent.duration.Duration
import scala.concurrent._
import model.types.Mailbox._
import model.types.Page._
import model.types.PerPage._
import model.types.ParticipantType._
import repositories.RepUtils.RepConstants._
import repositories.dtos._
import repositories.slick.mappings._
import repositories.RepUtils.RepMessages._
import repositories.RepUtils.types.OrderBy
import repositories.RepUtils.types.OrderBy._
import utils.TestGenerators._

import scala.concurrent._

import scala.concurrent._

class ChatsRepositorySpec extends AsyncWordSpec with OptionValues with MustMatchers
  with BeforeAndAfterAll with Inside with BeforeAndAfterEach with AppendedClues {

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

  def fillDB(addressRows: Seq[AddressRow] = Nil, chatRows: Seq[ChatRow] = Nil, userRows: Seq[UserRow] = Nil,
    userChatRows: Seq[UserChatRow] = Nil, emailRows: Seq[EmailRow] = Nil,
    emailAddressRows: Seq[EmailAddressRow] = Nil, oversightRows: Seq[OversightRow] = Nil): Future[Unit] =
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

    "return None if page is less than zero" in {
      for {
        optChatsPreview <- chatsRep.getChatsPreview(genMailbox.sample.value, choose(-10, -1).sample.value,
          choose(1, 10).sample.value, DefaultOrder, genUUID.sample.value)
      } yield optChatsPreview mustBe None
    }

    "return None if perPage is not greater than zero" in {
      for {
        optChatsPreview <- chatsRep.getChatsPreview(genMailbox.sample.value, choose(1, 10).sample.value.sample.value,
          choose(-10, 0).sample.value, DefaultOrder, genUUID.sample.value)
      } yield optChatsPreview mustBe None
    }

    "return None if perPage is greater than the maximum" in {
      for {
        optChatsPreview <- chatsRep.getChatsPreview(genMailbox.sample.value, choose(1, 10).sample.value.sample.value,
          choose(MAX_PER_PAGE + 1, MAX_PER_PAGE + 3).sample.value, DefaultOrder, genUUID.sample.value)
      } yield optChatsPreview mustBe None
    }

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

        chatsPreview <- chatsRep.getChatsPreview(Drafts, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Some((Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.addressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body)), 1, 0))
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

        chatsPreview <- chatsRep.getChatsPreview(Inbox, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Some(Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        senderAddressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body)), 1, 0)

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

        chatsPreview <- chatsRep.getChatsPreview(Inbox, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Some(Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        senderAddressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body)), 1, 0)
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

        chatsPreview <- chatsRep.getChatsPreview(Inbox, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Some(Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        senderAddressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body)), 1, 0)
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

        chatsPreview <- chatsRep.getChatsPreview(Inbox, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview.value._1 mustBe empty
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

        chatsPreview <- chatsRep.getChatsPreview(Inbox, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview.value._1 mustBe empty
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

        chatsPreview <- chatsRep.getChatsPreview(Inbox, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview.value._1 mustBe empty
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

        chatsPreview <- chatsRep.getChatsPreview(Inbox, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Some(Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.addressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body)), 1, 0)
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

        chatsPreview <- chatsRep.getChatsPreview(Sent, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Some(Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.addressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body)), 1, 0)
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

        chatsPreview <- chatsRep.getChatsPreview(Drafts, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Some(Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.addressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body)), 1, 0)
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

        chatsPreview <- chatsRep.getChatsPreview(Trash, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Some(Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.addressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body)), 1, 0)
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

        chatsPreview <- chatsRep.getChatsPreview(Inbox, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview.value._1 mustBe empty
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

        chatsPreview <- chatsRep.getChatsPreview(Sent, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview.value._1 mustBe empty
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

        chatsPreview <- chatsRep.getChatsPreview(Drafts, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview.value._1 mustBe empty
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

        chatsPreview <- chatsRep.getChatsPreview(Trash, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview.value._1 mustBe empty
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

        chatsPreview <- chatsRep.getChatsPreview(Inbox, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Some(Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.addressRow.address, "2019", basicTestDB.emailRow.body)), 1, 0)
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

        chatsPreview <- chatsRep.getChatsPreview(Inbox, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Some(Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        basicTestDB.addressRow.address, "2019", List(basicTestDB.emailRow, otherEmailRow).minBy(_.emailId).body)), 1, 0)

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

        chatsPreview <- chatsRep.getChatsPreview(Inbox, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Some(List(
        ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
          basicTestDB.addressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body),
        ChatPreview(otherChatRow.chatId, otherChatRow.subject,
          basicTestDB.addressRow.address, otherEmailRow.date, otherEmailRow.body))
        .sortBy(chatPreview =>
          (chatPreview.lastEmailDate, chatPreview.contentPreview, chatPreview.lastAddress))(
          Ordering.Tuple3(Ordering.String.reverse, Ordering.String, Ordering.String)), 2, 0)

    }

    "show more than one chat in ascending order of date" in {
      val basicTestDB = genBasicTestDB.sample.value
      val otherChatRow = genChatRow.sample.value
      val otherEmailRow = genEmailRow(otherChatRow.chatId).sample.value.copy(date = "2018")
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

        chatsPreview <- chatsRep.getChatsPreview(Inbox, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, Asc,
          basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Some(List(
        ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
          basicTestDB.addressRow.address, basicTestDB.emailRow.date, basicTestDB.emailRow.body),
        ChatPreview(otherChatRow.chatId, otherChatRow.subject,
          basicTestDB.addressRow.address, otherEmailRow.date, otherEmailRow.body))
        .sortBy(chatPreview =>
          (chatPreview.lastEmailDate, chatPreview.contentPreview, chatPreview.lastAddress)), 2, 0)
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

        chatsPreview <- chatsRep.getChatsPreview(Inbox, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Some(Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        overseeAddressRow.address, overseeEmailRow.date, overseeEmailRow.body)), 1, 0)

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

        chatsPreview <- chatsRep.getChatsPreview(Inbox, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview.value._1 mustBe empty
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

        chatsPreview <- chatsRep.getChatsPreview(Inbox, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Some(Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        senderAddressRow.address, overseeEmailRow.date, overseeEmailRow.body)), 1, 0)

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

        chatsPreview <- chatsRep.getChatsPreview(Inbox, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Some(Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        senderAddressRow.address, overseeEmailRow.date, overseeEmailRow.body)), 1, 0)
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

        chatsPreview <- chatsRep.getChatsPreview(Inbox, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview mustBe Some(Seq(ChatPreview(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        senderAddressRow.address, overseeEmailRow.date, overseeEmailRow.body)), 1, 0)

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

        chatsPreview <- chatsRep.getChatsPreview(Inbox, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview.value._1 mustBe empty
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

        chatsPreview <- chatsRep.getChatsPreview(Inbox, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview.value._1 mustBe empty
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

        chatsPreview <- chatsRep.getChatsPreview(Inbox, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield chatsPreview.value._1 mustBe empty
    }

    "return the correct totalCount and lastPage values" in {
      val basicTestDB = genBasicTestDB.sample.value
      val chatList = genList(1, 20, genChatRow).sample.value
      val userChatList = chatList.map(chatRow =>
        genUserChatRow(basicTestDB.userRow.userId, chatRow.chatId).sample.value)
      val emailList = chatList.map(chatRow => genEmailRow(chatRow.chatId).sample.value)
      val emailAddressList = emailList.map(emailRow => genEmailAddressRow(emailRow.emailId, emailRow.chatId,
        basicTestDB.addressRow.addressId, from).sample.value)
      val page = choose(0, 20).sample.value
      val perPage = choose(1, 20).sample.value
      val expectedLastPage = divide(chatList.size, perPage, RoundingMode.CEILING) - 1

      for {
        _ <- fillDB(
          addressRows = List(basicTestDB.addressRow),
          chatRows = chatList,
          userRows = List(basicTestDB.userRow),
          userChatRows = userChatList,
          emailRows = emailList,
          emailAddressRows = emailAddressList)

        optChatsPreview <- chatsRep.getChatsPreview(Inbox, page, perPage, DefaultOrder, basicTestDB.userRow.userId)
      } yield {

        val chatsPreview = optChatsPreview.value
        val chats = chatsPreview._1
        val totalCount = chatsPreview._2
        val lastPage = chatsPreview._3
        val sortedEmailList = emailList.sortBy(emailrow => (emailrow.date, emailrow.body))(
          Ordering.Tuple2(Ordering.String.reverse, Ordering.String))
        totalCount mustBe chatList.size withClue "The totalCount is wrong"
        assert(sortedEmailList.isDefinedAt(lastPage * perPage) &&
          !sortedEmailList.isDefinedAt((lastPage + 1) * perPage)) withClue "The value for the lastPage is wrong"
        lastPage mustBe expectedLastPage withClue "The value for the lastPage did not equal it's expected value"
      }
    }

    "sample the chats according to the given intermediary page and perPage values" in {
      val basicTestDB = genBasicTestDB.sample.value
      val chatList = genList(1, 20, genChatRow).sample.value
      val userChatList = chatList.map(chatRow =>
        genUserChatRow(basicTestDB.userRow.userId, chatRow.chatId).sample.value)
      val emailList = chatList.map(chatRow => genEmailRow(chatRow.chatId).sample.value)
      val emailAddressList = emailList.map(emailRow => genEmailAddressRow(emailRow.emailId, emailRow.chatId,
        basicTestDB.addressRow.addressId, from).sample.value)
      val perPage = choose(1, 20).sample.value
      val expectedLastPage = divide(chatList.size, perPage, RoundingMode.CEILING) - 1
      val page = choose(0, max(expectedLastPage - 1, 0)).sample.value

      for {
        _ <- fillDB(
          addressRows = List(basicTestDB.addressRow),
          chatRows = chatList,
          userRows = List(basicTestDB.userRow),
          userChatRows = userChatList,
          emailRows = emailList,
          emailAddressRows = emailAddressList)

        optChatsPreview <- chatsRep.getChatsPreview(Inbox, page, perPage, DefaultOrder, basicTestDB.userRow.userId)
      } yield {
        val chats = optChatsPreview.value._1
        val sortedEmailList = emailList.sortBy(emailrow => (emailrow.date, emailrow.body))(
          Ordering.Tuple2(Ordering.String.reverse, Ordering.String))
        chats.size mustBe min(perPage, chatList.size) withClue "The size of the sliced sequence is wrong"
        chats.headOption.value.contentPreview mustBe sortedEmailList(perPage * page).body withClue "The first element" +
          " of the sliced sequence is wrong"
      }
    }

    "correctly sample the last page of the chats" in {
      val basicTestDB = genBasicTestDB.sample.value
      val chatList = genList(1, 20, genChatRow).sample.value
      val userChatList = chatList.map(chatRow =>
        genUserChatRow(basicTestDB.userRow.userId, chatRow.chatId).sample.value)
      val emailList = chatList.map(chatRow => genEmailRow(chatRow.chatId).sample.value)
      val emailAddressList = emailList.map(emailRow => genEmailAddressRow(emailRow.emailId, emailRow.chatId,
        basicTestDB.addressRow.addressId, from).sample.value)
      val perPage = choose(1, 20).sample.value
      val expectedLastPage = divide(chatList.size, perPage, RoundingMode.CEILING) - 1

      for {
        _ <- fillDB(
          addressRows = List(basicTestDB.addressRow),
          chatRows = chatList,
          userRows = List(basicTestDB.userRow),
          userChatRows = userChatList,
          emailRows = emailList,
          emailAddressRows = emailAddressList)

        optChatsPreview <- chatsRep.getChatsPreview(Inbox, expectedLastPage, perPage, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield {
        val chatsPreview = optChatsPreview.value
        val chats = chatsPreview._1
        val totalCount = chatsPreview._2
        val sortedEmailList = emailList.sortBy(emailrow => (emailrow.date, emailrow.body))(
          Ordering.Tuple2(Ordering.String.reverse, Ordering.String))
        chats.size mustBe (totalCount - 1) - (perPage * expectedLastPage - 1) withClue "The size of the" +
          " sliced sequence is wrong"
        //            The size of the last Page must be equal to the index of the final element (totalCount - 1)
        //            minus the index of the last element of the penultimate page (perPage * expectedLastPage - 1).

        chats.headOption.value.contentPreview mustBe sortedEmailList(perPage * expectedLastPage).body withClue "The" +
          " first element of the sliced sequence is wrong"
      }
    }

    "return an empty sequence if the page is greater than the last page" in {
      val basicTestDB = genBasicTestDB.sample.value
      val chatList = genList(1, 20, genChatRow).sample.value
      val userChatList = chatList.map(chatRow =>
        genUserChatRow(basicTestDB.userRow.userId, chatRow.chatId).sample.value)
      val emailList = chatList.map(chatRow => genEmailRow(chatRow.chatId).sample.value)
      val emailAddressList = emailList.map(emailRow => genEmailAddressRow(emailRow.emailId, emailRow.chatId,
        basicTestDB.addressRow.addressId, from).sample.value)
      val perPage = choose(1, 20).sample.value
      val expectedLastPage = divide(chatList.size, perPage, RoundingMode.CEILING) - 1
      val page = choose(expectedLastPage + 1, expectedLastPage + 3).sample.value

      for {
        _ <- fillDB(
          addressRows = List(basicTestDB.addressRow),
          chatRows = chatList,
          userRows = List(basicTestDB.userRow),
          userChatRows = userChatList,
          emailRows = emailList,
          emailAddressRows = emailAddressList)

        optChatsPreview <- chatsRep.getChatsPreview(Inbox, page, perPage, DefaultOrder, basicTestDB.userRow.userId)
      } yield {
        val chatsPreview = optChatsPreview.value
        val chats = chatsPreview._1
        val totalCount = chatsPreview._2
        totalCount must be > 0
        chats mustBe empty
      }

    }
  }

  "SlickChatsRepository#getChat" should {

    "return INVALID_PAGINATION if page is less than zero" in {
      for {
        eitherResult <- chatsRep.getChat(genUUID.sample.value, choose(-10, -1).sample.value,
          choose(1, 10).sample.value, DefaultOrder, genUUID.sample.value)
      } yield eitherResult mustBe Left(INVALID_PAGINATION)
    }

    "return INVALID_PAGINATION if perPage is not greater than zero" in {
      for {
        eitherResult <- chatsRep.getChat(genUUID.sample.value, choose(1, 10).sample.value.sample.value,
          choose(-10, 0).sample.value, DefaultOrder, genUUID.sample.value)
      } yield eitherResult mustBe Left(INVALID_PAGINATION)
    }

    "return INVALID_PAGINATION if perPage is greater than the maximum" in {
      for {
        eitherResult <- chatsRep.getChat(genUUID.sample.value, choose(1, 10).sample.value.sample.value,
          choose(MAX_PER_PAGE + 1, MAX_PER_PAGE + 3).sample.value, DefaultOrder, genUUID.sample.value)
      } yield eitherResult mustBe Left(INVALID_PAGINATION)
    }

    "return CHAT_NOT_FOUND if the chat does not exist" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow))

        eitherResult <- chatsRep.getChat(genUUID.sample.value, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield eitherResult mustBe Left(CHAT_NOT_FOUND)

    }

    "return CHAT_NOT_FOUND if the chat exists but the User does not have access to it" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow))

        eitherResult <- chatsRep.getChat(genUUID.sample.value, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield eitherResult mustBe Left(CHAT_NOT_FOUND)

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

        eitherResult <- chatsRep.getChat(basicTestDB.chatRow.chatId, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
          DefaultOrder, basicTestDB.userRow.userId)
      } yield eitherResult mustBe Right((Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(basicTestDB.addressRow.address), Set(),
        Seq(Email(basicTestDB.emailRow.emailId, basicTestDB.addressRow.address, Set(), Set(), Set(),
          basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 0, Set()))), 1, 0))
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

        eitherResult <- chatsRep.getChat(basicTestDB.chatRow.chatId, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
          DefaultOrder, basicTestDB.userRow.userId)
      } yield eitherResult mustBe Right((Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(basicTestDB.addressRow.address, senderAddressRow.address), Set(),
        Seq(Email(basicTestDB.emailRow.emailId, senderAddressRow.address,
          Set(basicTestDB.addressRow.address), Set(), Set(),
          basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 1, Set()))), 1, 0))

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

        eitherResult <- chatsRep.getChat(basicTestDB.chatRow.chatId, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
          DefaultOrder, basicTestDB.userRow.userId)
      } yield eitherResult mustBe Right((Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(basicTestDB.addressRow.address, senderAddressRow.address), Set(),
        Seq(Email(basicTestDB.emailRow.emailId, senderAddressRow.address,
          Set(), Set(), Set(basicTestDB.addressRow.address),
          basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 1, Set()))), 1, 0))
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

        eitherResult <- chatsRep.getChat(basicTestDB.chatRow.chatId, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
          DefaultOrder, basicTestDB.userRow.userId)
      } yield eitherResult mustBe Right((Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(basicTestDB.addressRow.address, senderAddressRow.address), Set(),
        Seq(Email(basicTestDB.emailRow.emailId, senderAddressRow.address,
          Set(), Set(basicTestDB.addressRow.address), Set(),
          basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 1, Set()))), 1, 0))
    }

    "show the emails ordered by ascending date" in {
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

        eitherResult <- chatsRep.getChat(basicTestDB.chatRow.chatId, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
          Asc, basicTestDB.userRow.userId)
      } yield eitherResult mustBe Right((Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(basicTestDB.addressRow.address), Set(),
        Seq(
          Email(oldEmailRow.emailId, basicTestDB.addressRow.address, Set(), Set(), Set(),
            oldEmailRow.body, oldEmailRow.date, oldEmailRow.sent, Set()),
          Email(basicTestDB.emailRow.emailId, basicTestDB.addressRow.address, Set(), Set(), Set(),
            basicTestDB.emailRow.body, "2019", basicTestDB.emailRow.sent, Set()))), 2, 0))
    }

    "show the emails ordered by descending date" in {
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

        eitherResult <- chatsRep.getChat(basicTestDB.chatRow.chatId, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
          Desc, basicTestDB.userRow.userId)
      } yield eitherResult mustBe Right((Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(basicTestDB.addressRow.address), Set(),
        Seq(
          Email(basicTestDB.emailRow.emailId, basicTestDB.addressRow.address, Set(), Set(), Set(),
            basicTestDB.emailRow.body, "2019", basicTestDB.emailRow.sent, Set()),
          Email(oldEmailRow.emailId, basicTestDB.addressRow.address, Set(), Set(), Set(),
            oldEmailRow.body, oldEmailRow.date, oldEmailRow.sent, Set()))), 2, 0))
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

        eitherResult <- chatsRep.getChat(basicTestDB.chatRow.chatId, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
          DefaultOrder, basicTestDB.userRow.userId)
      } yield eitherResult mustBe Right((Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(overseeAddressRow.address),
        Set(Overseers(overseeAddressRow.address, Set(basicTestDB.addressRow.address))),
        Seq(Email(sentEmail.emailId, overseeAddressRow.address, Set(), Set(), Set(),
          sentEmail.body, sentEmail.date, sent = 1, Set()))), 1, 0))

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

        eitherResult <- chatsRep.getChat(basicTestDB.chatRow.chatId, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
          DefaultOrder, basicTestDB.userRow.userId)
      } yield eitherResult mustBe Right((Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(overseeAddressRow.address, senderAddressRow.address),
        Set(Overseers(overseeAddressRow.address, Set(basicTestDB.addressRow.address))),
        Seq(Email(sentEmail.emailId, senderAddressRow.address, Set(overseeAddressRow.address), Set(), Set(),
          sentEmail.body, sentEmail.date, sent = 1, Set()))), 1, 0))

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

        eitherResult <- chatsRep.getChat(basicTestDB.chatRow.chatId, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
          DefaultOrder, basicTestDB.userRow.userId)
      } yield eitherResult mustBe Right((Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(overseeAddressRow.address, senderAddressRow.address),
        Set(Overseers(overseeAddressRow.address, Set(basicTestDB.addressRow.address))),
        Seq(Email(sentEmail.emailId, senderAddressRow.address, Set(), Set(), Set(overseeAddressRow.address),
          sentEmail.body, sentEmail.date, sent = 1, Set()))), 1, 0))

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

        eitherResult <- chatsRep.getChat(basicTestDB.chatRow.chatId, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
          DefaultOrder, basicTestDB.userRow.userId)
      } yield eitherResult mustBe Right((Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
        Set(overseeAddressRow.address, senderAddressRow.address),
        Set(Overseers(overseeAddressRow.address, Set(basicTestDB.addressRow.address))),
        Seq(Email(sentEmail.emailId, senderAddressRow.address, Set(), Set(overseeAddressRow.address), Set(),
          sentEmail.body, sentEmail.date, sent = 1, Set()))), 1, 0))

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

        val visibleBccChat = Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
          Set(basicTestDB.addressRow.address, toAddressRow.address, ccAddressRow.address, bccAddressRow.address),
          Set(
            Overseers(basicTestDB.addressRow.address, Set(fromOverseerAddressRow.address)),
            Overseers(toAddressRow.address, Set(toOverseerAddressRow.address)),
            Overseers(ccAddressRow.address, Set(ccOverseerAddressRow.address)),
            Overseers(bccAddressRow.address, Set(bccOverseerAddressRow.address))),
          Seq(Email(basicTestDB.emailRow.emailId, basicTestDB.addressRow.address,
            Set(toAddressRow.address), Set(bccAddressRow.address), Set(ccAddressRow.address),
            basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 1, Set())))

        val notVisibleBccChat = Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
          Set(basicTestDB.addressRow.address, toAddressRow.address, ccAddressRow.address),
          Set(
            Overseers(basicTestDB.addressRow.address, Set(fromOverseerAddressRow.address)),
            Overseers(toAddressRow.address, Set(toOverseerAddressRow.address)),
            Overseers(ccAddressRow.address, Set(ccOverseerAddressRow.address)),
            Overseers(bccAddressRow.address, Set(bccOverseerAddressRow.address))),
          Seq(Email(basicTestDB.emailRow.emailId, basicTestDB.addressRow.address,
            Set(toAddressRow.address), Set(), Set(ccAddressRow.address),
            basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 1, Set())))

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

          resultOverFrom <- chatsRep.getChat(basicTestDB.chatRow.chatId, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
            DefaultOrder, fromOverseerUserRow.userId)
          resultOverTo <- chatsRep.getChat(basicTestDB.chatRow.chatId, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
            DefaultOrder, toOverseerUserRow.userId)
          resultOverCC <- chatsRep.getChat(basicTestDB.chatRow.chatId, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
            DefaultOrder, ccOverseerUserRow.userId)
          resultOverBCC <- chatsRep.getChat(basicTestDB.chatRow.chatId, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
            DefaultOrder, bccOverseerUserRow.userId)

        } yield {
          resultOverFrom.toOption.value._1 mustBe visibleBccChat withClue "The result for the Overseer of the " +
            "From was not equal to the chat that includes the Bcc"
          resultOverTo.toOption.value._1 mustBe notVisibleBccChat withClue "The result for the Overseer of the " +
            "To  was not equal to the chat that excludes the Bcc"
          resultOverCC.toOption.value._1 mustBe notVisibleBccChat withClue "The result for the Overseer of the " +
            "CC  was not equal to the chat that excludes the Bcc"
          resultOverBCC.toOption.value._1 mustBe visibleBccChat withClue "The result for the Overseer of the " +
            "BCC  was not equal to the chat that includes the Bcc"
        }

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

          resultOverFrom <- chatsRep.getChat(basicTestDB.chatRow.chatId, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
            DefaultOrder, fromOverseerUserRow.userId)
          resultOverBCCOne <- chatsRep.getChat(basicTestDB.chatRow.chatId, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
            DefaultOrder, bccOneOverseerUserRow.userId)
          resultOverBCCTwo <- chatsRep.getChat(basicTestDB.chatRow.chatId, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
            DefaultOrder, bccTwoOverseerUserRow.userId)

        } yield {
          resultOverFrom.toOption.value._1 mustBe Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
            Set(basicTestDB.addressRow.address, bccOneAddressRow.address, bccTwoAddressRow.address),
            Set(
              Overseers(basicTestDB.addressRow.address, Set(fromOverseerAddressRow.address)),
              Overseers(bccOneAddressRow.address, Set(bccOneOverseerAddressRow.address)),
              Overseers(bccTwoAddressRow.address, Set(bccTwoOverseerAddressRow.address))),
            Seq(Email(basicTestDB.emailRow.emailId, basicTestDB.addressRow.address,
              Set(), Set(bccOneAddressRow.address, bccTwoAddressRow.address), Set(),
              basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 1, Set()))) withClue "The chat viewed by" +
            "the overseer of the sender is incorrect"

          resultOverBCCOne.toOption.value._1 mustBe Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
            Set(basicTestDB.addressRow.address, bccOneAddressRow.address),
            Set(
              Overseers(basicTestDB.addressRow.address, Set(fromOverseerAddressRow.address)),
              Overseers(bccOneAddressRow.address, Set(bccOneOverseerAddressRow.address)),
              Overseers(bccTwoAddressRow.address, Set(bccTwoOverseerAddressRow.address))),
            Seq(Email(basicTestDB.emailRow.emailId, basicTestDB.addressRow.address,
              Set(), Set(bccOneAddressRow.address), Set(),
              basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 1, Set()))) withClue "The chat viewed by" +
            "the overseer of the first Bcc is incorrect"

          resultOverBCCTwo.toOption.value._1 mustBe Chat(basicTestDB.chatRow.chatId, basicTestDB.chatRow.subject,
            Set(basicTestDB.addressRow.address, bccTwoAddressRow.address),
            Set(
              Overseers(basicTestDB.addressRow.address, Set(fromOverseerAddressRow.address)),
              Overseers(bccOneAddressRow.address, Set(bccOneOverseerAddressRow.address)),
              Overseers(bccTwoAddressRow.address, Set(bccTwoOverseerAddressRow.address))),
            Seq(Email(basicTestDB.emailRow.emailId, basicTestDB.addressRow.address,
              Set(), Set(bccTwoAddressRow.address), Set(),
              basicTestDB.emailRow.body, basicTestDB.emailRow.date, sent = 1, Set()))) withClue "The chat viewed by" +
            "the overseer of the second Bcc is incorrect"
        }

      }

    "return the correct totalCount and lastPage values" in {
      val basicTestDB = genBasicTestDB.sample.value
      val emailRows = genList(1, 20, genEmailRow(basicTestDB.chatRow.chatId)).sample.value
      val emailAddressRows = emailRows.map(emailRow => genEmailAddressRow(emailRow.emailId, basicTestDB.chatRow.chatId,
        basicTestDB.addressRow.addressId, From).sample.value)
      val sortedEmails = emailRows.map(emailRow => Email(emailRow.emailId, basicTestDB.addressRow.address, Set(), Set(),
        Set(), emailRow.body, emailRow.date, emailRow.sent, Set())).sortBy(email => (email.date, email.body))

      val perPage = choose(1, 20).sample.value
      val expectedLastPage = divide(emailRows.size, perPage, RoundingMode.CEILING) - 1
      val page = choose(0, expectedLastPage + 1).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          emailRows,
          emailAddressRows)

        eitherResult <- chatsRep.getChat(basicTestDB.chatRow.chatId, page, perPage, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield {
        eitherResult match {
          case Right((_, totalCount, lastPage)) =>
            totalCount mustBe emailRows.size withClue "The totalCount is wrong"
            assert(sortedEmails.isDefinedAt(lastPage * perPage) &&
              !sortedEmails.isDefinedAt((lastPage + 1) * perPage)) withClue "The value for the lastPage is wrong"
            lastPage mustBe expectedLastPage withClue "The value for the lastPage did not equal it's expected value"

          case Left(message) => fail(message)
        }
      }

    }

    "sample the emails according to the given intermediary page and perPage values" in {
      val basicTestDB = genBasicTestDB.sample.value
      val emailRows = genList(1, 20, genEmailRow(basicTestDB.chatRow.chatId)).sample.value
      val emailAddressRows = emailRows.map(emailRow => genEmailAddressRow(emailRow.emailId, basicTestDB.chatRow.chatId,
        basicTestDB.addressRow.addressId, From).sample.value)
      val sortedEmails = emailRows.map(emailRow => Email(emailRow.emailId, basicTestDB.addressRow.address, Set(), Set(),
        Set(), emailRow.body, emailRow.date, emailRow.sent, Set())).sortBy(email => (email.date, email.body))

      val perPage = choose(1, 20).sample.value
      val expectedLastPage = divide(emailRows.size, perPage, RoundingMode.CEILING) - 1
      val page = choose(0, max(expectedLastPage - 1, 0)).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          emailRows,
          emailAddressRows)

        eitherResult <- chatsRep.getChat(basicTestDB.chatRow.chatId, page, perPage, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield {
        eitherResult match {
          case Right((chat, _, _)) =>
            val emails = chat.emails

            emails.size mustBe min(perPage, emailRows.size) withClue "The size of the slice sequence is wrong"
            emails.headOption.value mustBe sortedEmails(perPage * page) withClue ("The first element" +
              " of the sliced sequence is wrong")

          case Left(message) => fail(message)
        }
      }

    }

    "correctly sample the last page of the emails" in {
      val basicTestDB = genBasicTestDB.sample.value
      val emailRows = genList(1, 20, genEmailRow(basicTestDB.chatRow.chatId)).sample.value
      val emailAddressRows = emailRows.map(emailRow => genEmailAddressRow(emailRow.emailId, basicTestDB.chatRow.chatId,
        basicTestDB.addressRow.addressId, From).sample.value)
      val sortedEmails = emailRows.map(emailRow => Email(emailRow.emailId, basicTestDB.addressRow.address, Set(), Set(),
        Set(), emailRow.body, emailRow.date, emailRow.sent, Set())).sortBy(email => (email.date, email.body))

      val perPage = choose(1, 20).sample.value
      val expectedLastPage = divide(emailRows.size, perPage, RoundingMode.CEILING) - 1

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          emailRows,
          emailAddressRows)

        eitherResult <- chatsRep.getChat(basicTestDB.chatRow.chatId, expectedLastPage, perPage, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield {
        eitherResult match {
          case Right((chat, totalCount, _)) =>
            val emails = chat.emails

            emails.size mustBe (totalCount - 1) - (perPage * expectedLastPage - 1) withClue "The size of the" +
              " sliced sequence is wrong"
            //            The size of the last Page must be equal to the index of the final element (totalCount - 1)
            //            minus the index of the last element of the penultimate page (perPage * expectedLastPage - 1).

            emails.headOption.value mustBe sortedEmails(perPage * expectedLastPage) withClue "The first" +
              " element of the sliced sequence is wrong"

          case Left(message) => fail(message)
        }
      }

    }

    "return an empty sequence if the page is greater than the last page" in {
      val basicTestDB = genBasicTestDB.sample.value
      val emailRows = genList(1, 20, genEmailRow(basicTestDB.chatRow.chatId)).sample.value
      val emailAddressRows = emailRows.map(emailRow => genEmailAddressRow(emailRow.emailId, basicTestDB.chatRow.chatId,
        basicTestDB.addressRow.addressId, From).sample.value)
      val sortedEmails = emailRows.map(emailRow => Email(emailRow.emailId, basicTestDB.addressRow.address, Set(), Set(),
        Set(), emailRow.body, emailRow.date, emailRow.sent, Set())).sortBy(email => (email.date, email.body))

      val perPage = choose(1, 20).sample.value
      val expectedLastPage = divide(emailRows.size, perPage, RoundingMode.CEILING) - 1
      val page = choose(expectedLastPage + 1, expectedLastPage + 3).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          emailRows,
          emailAddressRows)

        eitherResult <- chatsRep.getChat(basicTestDB.chatRow.chatId, page, perPage, DefaultOrder,
          basicTestDB.userRow.userId)
      } yield {
        eitherResult match {
          case Right((chat, totalCount, _)) =>
            totalCount must be > 0
            chat.emails mustBe empty

          case Left(message) => fail(message)
        }
      }

    }

  }

  "SlickChatsRepository#postChat" should {

    "not post the chat if the userId does not have a corresponding address" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          userRows = List(basicTestDB.userRow))
        postResponse <- chatsRep.postChat(genCreateChatOption.sample.value, basicTestDB.userRow.userId)
      } yield postResponse mustBe None

    }

    "create a chat with an email draft for a user and then get the same chat for the same user: results must match" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))
        postResponse <- chatsRep.postChat(genCreateChatOption.sample.value, basicTestDB.userRow.userId)
        getResponse <- chatsRep.getChat(postResponse.value.chatId.get, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
          DefaultOrder, basicTestDB.userRow.userId)
      } yield getResponse.toOption.value._1 mustBe CreateChat.fromCreateChatToChat(postResponse.value)

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
            receiverUserRow.userId)
          getResponse <- chatsRep.getChat(postResponse.value.chatId.value, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
            DefaultOrder, basicTestDB.userRow.userId)
        } yield getResponse mustBe Left(CHAT_NOT_FOUND)

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
        getResponse <- chatsRep.getChat(postResponse.value.chatId.value, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
          DefaultOrder, receiverUserRow.userId)
      } yield getResponse mustBe Left(CHAT_NOT_FOUND)

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
        getResponse <- chatsRep.getChat(postResponse.value.chatId.value, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
          DefaultOrder, receiverUserRow.userId)
      } yield getResponse mustBe Left(CHAT_NOT_FOUND)

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
        getResponse <- chatsRep.getChat(postResponse.value.chatId.get, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
          DefaultOrder, basicTestDB.userRow.userId)
      } yield getResponse.toOption.value._1 mustBe CreateChat.fromCreateChatToChat(postResponse.value)

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
        postEmailResponse <- chatsRep.postEmail(genUpsertEmailOption.sample.value, postChatResponse.value.chatId.value,
          basicTestDB.userRow.userId)
        getResponse <- chatsRep.getChat(postChatResponse.value.chatId.value, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
          DefaultOrder, basicTestDB.userRow.userId)
        nrDrafts <- db.run(UserChatsTable.all.filter(_.userId === basicTestDB.userRow.userId).map(_.draft)
          .result.headOption)

      } yield assert(getResponse.toOption.value._1 === {
        val originalChat = CreateChat.fromCreateChatToChat(postChatResponse.value)
        originalChat.copy(
          emails = (UpsertEmail.fromUpsertEmailToEmail(postEmailResponse.value.email) +: originalChat.emails)
            .sortBy(email => (email.date, email.body)),
          addresses = addressesFromUpsertEmail(postEmailResponse.value.email) ++ originalChat.addresses)
      } & nrDrafts.value === 2)

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
        getPostedEmail = CreateChat.fromCreateChatToChat(postChat.value).emails.headOption.value

        patchBody = genString.sample.value

        patchEmail <- chatsRep.patchEmail(
          UpsertEmail(None, None, None, None, None, Some(patchBody), None, None),
          postChat.value.chatId.value, postChat.value.email.emailId.value, basicTestDB.userRow.userId)
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
          getPostedChat = CreateChat.fromCreateChatToChat(postChat.value)
          getPostedEmail = CreateChat.fromCreateChatToChat(postChat.value).emails.headOption.value

          patchEmail <- chatsRep.patchEmail(
            UpsertEmail(None, None, Some(Set(toAddressRow.address)),
              Some(Set(bccAddressRow.address)), Some(Set(ccAddressRow.address)), None, None, Some(true)),
            postChat.value.chatId.value, postChat.value.email.emailId.value,
            basicTestDB.userRow.userId)

          fromUserGetChat <- chatsRep.getChat(postChat.value.chatId.value, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
            DefaultOrder, basicTestDB.userRow.userId).map(_.toOption.value._1)
          toUserGetChat <- chatsRep.getChat(postChat.value.chatId.value, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
            DefaultOrder, toUserRow.userId).map(_.toOption.value._1)
          ccUserGetChat <- chatsRep.getChat(postChat.value.chatId.value, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
            DefaultOrder, ccUserRow.userId).map(_.toOption.value._1)
          bccUserGetChat <- chatsRep.getChat(postChat.value.chatId.value, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
            DefaultOrder, bccUserRow.userId).map(_.toOption.value._1)

          senderChatsPreviewSent <- chatsRep.getChatsPreview(Sent, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
            DefaultOrder, basicTestDB.userRow.userId).map(_.value._1)
          senderChatsPreviewDrafts <- chatsRep.getChatsPreview(Drafts, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
            DefaultOrder, basicTestDB.userRow.userId).map(_.value._1)

          toReceiverChatsPreviewInbox <- chatsRep.getChatsPreview(Inbox, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
            DefaultOrder, toUserRow.userId).map(_.value._1)
          ccReceiverChatsPreviewInbox <- chatsRep.getChatsPreview(Inbox, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
            DefaultOrder, ccUserRow.userId).map(_.value._1)
          bccReceiverChatsPreviewInbox <- chatsRep.getChatsPreview(Inbox, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
            DefaultOrder, bccUserRow.userId).map(_.value._1)
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
          patchEmail.value === visibleBccExpectedEmailAfterPatch &
            fromUserGetChat === visibleBccExpectedChatAfterPatch &
            toUserGetChat === invisibleBccExpectedChatAfterPatch &
            ccUserGetChat === invisibleBccExpectedChatAfterPatch &
            bccUserGetChat === visibleBccExpectedChatAfterPatch &

            senderChatsPreviewSent.contains(expectedChatPreview) &
            !senderChatsPreviewDrafts.contains(expectedChatPreview) &

            toReceiverChatsPreviewInbox.contains(expectedChatPreview) &
            ccReceiverChatsPreviewInbox.contains(expectedChatPreview) &
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
          postChat.value.chatId.value, postChat.value.email.emailId.value, basicTestDB.userRow.userId)
      } yield assert(
        patchEmail.value === CreateChat.fromCreateChatToChat(postChat.value).emails.headOption.value &
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
        getPostedEmail = CreateChat.fromCreateChatToChat(postChat.value).emails.headOption.value

        patchEmail <- chatsRep.patchEmail(
          UpsertEmail(None, None, None, None, None, Some(genString.sample.value), None, None),
          postChat.value.chatId.value, postChat.value.email.emailId.value, otherUserRow.userId)
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
        getPostedEmail = CreateChat.fromCreateChatToChat(postChat.value).emails.headOption.value

        patchEmail <- chatsRep.patchEmail(
          UpsertEmail(None, None, None, None, None, None, None, Some(true)),
          postChat.value.chatId.value, postChat.value.email.emailId.value, basicTestDB.userRow.userId)

        retryPatchEmailAfterSent <- chatsRep.patchEmail(
          UpsertEmail(None, None, None, None, None, Some(genString.sample.value), None, None),
          postChat.value.chatId.value, postChat.value.email.emailId.value, basicTestDB.userRow.userId)

      } yield retryPatchEmailAfterSent mustBe None
    }

    "return None if the requested emailId is not a part of the chat with the specified chatId" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          addressRows = List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))
        postChat <- chatsRep.postChat(genCreateChatOption.sample.value, basicTestDB.userRow.userId)
        createdChatId = postChat.value.chatId.value

        patchEmail <- chatsRep.patchEmail(
          UpsertEmail(None, None, None, None, None, Some(genString.sample.value), None, None),
          postChat.value.chatId.value, genUUID.sample.value, basicTestDB.userRow.userId)
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
        result === Some(MoveToTrash) &
          userChat.inbox === 0 &
          userChat.sent === 0 &
          userChat.draft === 0 &
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
        _ <- chatsRep.postEmail(genUpsertEmailOption.sample.value, createdChatDTO.value.chatId.value,
          basicTestDB.userRow.userId)
        _ <- chatsRep.patchEmail(
          UpsertEmail(None, None, None, None, None, None, None, Some(true)),
          createdChatDTO.value.chatId.value, createdChatDTO.value.email.emailId.value, basicTestDB.userRow.userId)
        _ <- chatsRep.patchChat(MoveToTrash, createdChatDTO.value.chatId.value, basicTestDB.userRow.userId)
        result <- chatsRep.patchChat(Restore, createdChatDTO.value.chatId.value, basicTestDB.userRow.userId)
        optUserChat <- db.run(UserChatsTable.all.filter(uc => uc.chatId === createdChatDTO.value.chatId.value
          && uc.userId === basicTestDB.userRow.userId)
          .result.headOption)
        userChat = optUserChat.value
      } yield assert(
        result === Some(Restore) &
          userChat.inbox === 1 &
          userChat.sent === 1 &
          userChat.draft === 1 &
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
        result === Some(Restore) &
          userChat.inbox === 1 &
          userChat.sent === 0 &
          userChat.draft === 0 &
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
      } yield assert(result === None & optUserChat === None)

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
        getPatchedChat <- chatsRep.getChat(createdChatDTO.value.chatId.value, DEFAULT_PAGE.value,
          DEFAULT_PER_PAGE.value, DefaultOrder, basicTestDB.userRow.userId)

      } yield assert(
        result === Some(ChangeSubject(newSubject)) &
          getPatchedChat.toOption.value._1.subject === newSubject)
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
          createdChatDTO.value.chatId.value, createdChatDTO.value.email.emailId.value, basicTestDB.userRow.userId)

        oldSubject = createdChatDTO.value.subject.getOrElse("")
        result <- chatsRep.patchChat(ChangeSubject(genString.sample.value), createdChatDTO.value.chatId.value,
          basicTestDB.userRow.userId)
        getChat <- chatsRep.getChat(createdChatDTO.value.chatId.value, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
          DefaultOrder, basicTestDB.userRow.userId)

      } yield assert(result === None &
        getChat.toOption.value._1.subject === oldSubject)
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
        deleteDefinitely &
          userChat.inbox === 0 &
          userChat.sent === 0 &
          userChat.draft === 0 &
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
        !deleteTry &
          chatBeforeDeleteTry.value.inbox === chatAfterDeleteTry.value.inbox &
          chatBeforeDeleteTry.value.sent === chatAfterDeleteTry.value.sent &
          chatBeforeDeleteTry.value.draft === chatAfterDeleteTry.value.draft &
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
        !deleteDefinitely &
          userChat.inbox === 0 &
          userChat.sent === 0 &
          userChat.draft === 0 &
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
        deleteDefinitely &
          basicTestDB.userChatRow.inbox === overseerUserChatAfter.value.inbox &
          basicTestDB.userChatRow.sent === overseerUserChatAfter.value.sent &
          basicTestDB.userChatRow.draft === overseerUserChatAfter.value.draft &
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
      } yield assert(!deleteDraft & emailRow.nonEmpty & emailAddressesRows.nonEmpty)
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
          createdChatDTO.value.chatId.value, createdChatDTO.value.email.emailId.value, basicTestDB.userRow.userId)
        _ <- chatsRep.postEmail(genUpsertEmailOption.sample.value, createdChatDTO.value.chatId.value,
          basicTestDB.userRow.userId)
        _ <- chatsRep.postEmail(genUpsertEmailOption.sample.value, createdChatDTO.value.chatId.value,
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

      } yield assert(!deleteDraft & getEmail.nonEmpty &
        emailRow.nonEmpty & emailAddressesRows.nonEmpty &
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

        _ <- db.run(AttachmentsTable.all += AttachmentRow(genUUID.sample.value, basicTestDB.emailRow.emailId, genString.sample.value, genString.sample.value, Some(genString.sample.value)))

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

      } yield assert(deleteDraft & getEmail.isEmpty &
        emailRow.isEmpty & emailAddressesRows.isEmpty & attachmentsRows.isEmpty &
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

          _ <- db.run(AttachmentsTable.all += AttachmentRow(genUUID.sample.value, basicTestDB.emailRow.emailId, genString.sample.value, genString.sample.value, Some(genString.sample.value)))

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

        } yield assert(deleteDraft & getEmail.isEmpty &
          emailRow.isEmpty & emailAddressesRows.isEmpty & attachmentsRows.isEmpty &
          userChatRow.isEmpty & chatRow.isEmpty)
      }

    "not allow a draft to be patched after it was deleted" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))
        createdDraft <- chatsRep.postChat(genCreateChatOption.sample.value, basicTestDB.userRow.userId)
        deleteDraft <- chatsRep.deleteDraft(createdDraft.value.chatId.value, createdDraft.value.email.emailId.value,
          basicTestDB.userRow.userId)

        tryGetEmailBefore <- chatsRep.getEmail(createdDraft.value.chatId.value, createdDraft.value.email.emailId.value,
          basicTestDB.userRow.userId)
        tryPatch <- chatsRep.patchEmail(genUpsertEmailOption.sample.value, createdDraft.value.chatId.value,
          createdDraft.value.email.emailId.value, basicTestDB.userRow.userId)
        tryGetEmailAfter <- chatsRep.getEmail(createdDraft.value.chatId.value, createdDraft.value.email.emailId.value,
          basicTestDB.userRow.userId)
      } yield assert(tryPatch.isEmpty & tryGetEmailBefore === tryGetEmailAfter & tryGetEmailAfter === None)
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
          createdChatDTO.value.chatId.value, createdChatDTO.value.email.emailId.value, overseerUserRow.userId)

        optSetOverseer <- chatsRep.postOverseers(setPostOverseer, createdChatDTO.value.chatId.value,
          basicTestDB.userRow.userId)

        optOversightId <- db.run(OversightsTable.all.filter(_.overseerId === overseerUserRow.userId)
          .map(_.oversightId).result.headOption)

        optOverseerUserChat <- db.run(UserChatsTable.all.filter(_.userId === overseerUserRow.userId).result.headOption)

      } yield assert(
        optSetOverseer.value === setPostOverseer.map(_.copy(oversightId = Some(optOversightId.value))) &
          optOverseerUserChat.value.inbox === 1 &
          optOverseerUserChat.value.sent === 1 &
          optOverseerUserChat.value.draft === 0 &
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
        optSetOverseer.value === setPostOverseer.map(_.copy(oversightId = Some(optOversightId.value))) &
          optOverseerUserChat.value.inbox === 1 &
          optOverseerUserChat.value.sent === 0 &
          optOverseerUserChat.value.draft === 0 &
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
        overseersIds.contains(overseerOneUserRow.userId) &
          overseersIds.contains(overseerTwoUserRow.userId) &
          optOverseerOneUserChat.value.inbox === 1 &
          optOverseerTwoUserChat.value.inbox === 1)

    }

  }

  "SlickChatsRepository#getOverseers" should {

    "return INVALID_PAGINATION if page is less than zero" in {
      for {
        result <- chatsRep.getOverseers(genUUID.sample.value, choose(-10, -1).sample.value,
          choose(1, 10).sample.value, DefaultOrder, genUUID.sample.value)
      } yield result mustBe Left(INVALID_PAGINATION)
    }

    "return INVALID_PAGINATION if perPage is not greater than zero" in {
      for {
        result <- chatsRep.getOverseers(genUUID.sample.value, choose(1, 10).sample.value.sample.value,
          choose(-10, 0).sample.value, DefaultOrder, genUUID.sample.value)
      } yield result mustBe Left(INVALID_PAGINATION)
    }

    "return INVALID_PAGINATION if perPage is greater than the maximum" in {
      for {
        result <- chatsRep.getOverseers(genUUID.sample.value, choose(1, 10).sample.value.sample.value,
          choose(MAX_PER_PAGE + 1, MAX_PER_PAGE + 3).sample.value, DefaultOrder, genUUID.sample.value)
      } yield result mustBe Left(INVALID_PAGINATION)
    }

    "return CHAT_NOT_FOUND if the chat does not exist" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow))

        result <- chatsRep.getOverseers(genUUID.sample.value, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
          DefaultOrder, basicTestDB.userRow.userId)
      } yield result mustBe Left(CHAT_NOT_FOUND)

    }

    "return CHAT_NOT_FOUND if the chat exists but the User does not have access to it" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow))

        result <- chatsRep.getOverseers(genUUID.sample.value, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
          DefaultOrder, basicTestDB.userRow.userId)
      } yield result mustBe Left(CHAT_NOT_FOUND)

    }

    "return the user's overseers in ascending alphabetical order of overseer address" in {
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

        postedOverseers <- chatsRep.postOverseers(setPostOverseer, createdChatDTO.value.chatId.value,
          basicTestDB.userRow.userId)

        result <- chatsRep.getOverseers(createdChatDTO.value.chatId.value, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
          Asc, basicTestDB.userRow.userId)

      } yield result mustBe Right((postedOverseers.value.toSeq.sortBy {
        case PostOverseer(address, optOversightId) =>
          (address, optOversightId.value)
      }, 2, 0))

    }

    "return the user's overseers in descending alphabetical order of overseer address" in {
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

        postedOverseers <- chatsRep.postOverseers(setPostOverseer, createdChatDTO.value.chatId.value,
          basicTestDB.userRow.userId)

        result <- chatsRep.getOverseers(createdChatDTO.value.chatId.value, DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value,
          Desc, basicTestDB.userRow.userId)

      } yield result mustBe Right((postedOverseers.value.toSeq.sortBy {
        case PostOverseer(address, optOversightId) =>
          (address, optOversightId.value)
      }(Ordering.Tuple2(Ordering.String.reverse, Ordering.String)), 2, 0))

    }

    "return the correct totalCount and lastPage values" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseerAddressList = genList(1, 20, genAddressRow).sample.value
      val overseerUserList = overseerAddressList.map(addressRow => genUserRow(addressRow.addressId).sample.value)
      val seqPostOverseer = overseerAddressList.map(addressRow => PostOverseer(addressRow.address, None))
      val perPage = choose(1, 20).sample.value
      val expectedLastPage = divide(overseerAddressList.size, perPage, RoundingMode.CEILING) - 1
      val page = choose(0, expectedLastPage + 1).sample.value

      for {
        _ <- fillDB(
          addressRows = List(basicTestDB.addressRow) ++ overseerAddressList,
          userRows = List(basicTestDB.userRow) ++ overseerUserList)

        createdChatDTO <- chatsRep.postChat(genCreateChatOption.sample.value, basicTestDB.userRow.userId)

        postedOverseers <- chatsRep.postOverseers(seqPostOverseer.toSet, createdChatDTO.value.chatId.value,
          basicTestDB.userRow.userId)

        eitherResult <- chatsRep.getOverseers(createdChatDTO.value.chatId.value, page, perPage,
          DefaultOrder, basicTestDB.userRow.userId)

      } yield {
        eitherResult match {
          case Right((_, totalCount, lastPage)) =>
            val sortedOverseers = postedOverseers.value.toSeq.sortBy {
              case PostOverseer(address, optOversightId) =>
                (address, optOversightId.value)
            }
            totalCount mustBe overseerAddressList.size withClue "The totalCount is wrong"
            assert(sortedOverseers.isDefinedAt(lastPage * perPage) &&
              !sortedOverseers.isDefinedAt((lastPage + 1) * perPage)) withClue "The value for the lastPage is wrong"
            lastPage mustBe expectedLastPage withClue "The value for the lastPage did not equal it's expected value"

          case Left(message) => fail(message)
        }
      }
    }

    "sample the overseers according to the given intermediary page and perPage values" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseerAddressList = genList(1, 20, genAddressRow).sample.value
      val overseerUserList = overseerAddressList.map(addressRow => genUserRow(addressRow.addressId).sample.value)
      val seqPostOverseer = overseerAddressList.map(addressRow => PostOverseer(addressRow.address, None))
      val perPage = choose(1, 20).sample.value
      val expectedLastPage = divide(overseerAddressList.size, perPage, RoundingMode.CEILING) - 1
      val page = choose(0, max(expectedLastPage - 1, 0)).sample.value

      for {
        _ <- fillDB(
          addressRows = List(basicTestDB.addressRow) ++ overseerAddressList,
          userRows = List(basicTestDB.userRow) ++ overseerUserList)

        createdChatDTO <- chatsRep.postChat(genCreateChatOption.sample.value, basicTestDB.userRow.userId)

        postedOverseers <- chatsRep.postOverseers(seqPostOverseer.toSet, createdChatDTO.value.chatId.value,
          basicTestDB.userRow.userId)

        eitherResult <- chatsRep.getOverseers(createdChatDTO.value.chatId.value, page, perPage,
          DefaultOrder, basicTestDB.userRow.userId)

      } yield {
        eitherResult match {
          case Right((overseers, _, _)) =>
            val sortedOverseers = postedOverseers.value.toSeq.sortBy {
              case PostOverseer(address, optOversightId) =>
                (address, optOversightId.value)
            }
            overseers.size mustBe min(perPage, seqPostOverseer.size) withClue "The size of the slice sequence is wrong"
            overseers.headOption.value mustBe sortedOverseers(perPage * page) withClue ("The first element" +
              " of the sliced sequence is wrong")
          case Left(message) => fail(message)
        }
      }
    }

    "correctly sample the last page of the overseers" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseerAddressList = genList(1, 20, genAddressRow).sample.value
      val overseerUserList = overseerAddressList.map(addressRow => genUserRow(addressRow.addressId).sample.value)
      val seqPostOverseer = overseerAddressList.map(addressRow => PostOverseer(addressRow.address, None))
      val perPage = choose(1, 20).sample.value
      val expectedLastPage = divide(overseerAddressList.size, perPage, RoundingMode.CEILING) - 1

      for {
        _ <- fillDB(
          addressRows = List(basicTestDB.addressRow) ++ overseerAddressList,
          userRows = List(basicTestDB.userRow) ++ overseerUserList)

        createdChatDTO <- chatsRep.postChat(genCreateChatOption.sample.value, basicTestDB.userRow.userId)

        postedOverseers <- chatsRep.postOverseers(seqPostOverseer.toSet, createdChatDTO.value.chatId.value,
          basicTestDB.userRow.userId)

        eitherResult <- chatsRep.getOverseers(createdChatDTO.value.chatId.value, expectedLastPage, perPage,
          DefaultOrder, basicTestDB.userRow.userId)

      } yield {
        eitherResult match {
          case Right((overseers, totalCount, _)) =>
            val sortedOverseers = postedOverseers.value.toSeq.sortBy {
              case PostOverseer(address, optOversightId) =>
                (address, optOversightId.value)
            }
            overseers.size mustBe (totalCount - 1) - (perPage * expectedLastPage - 1) withClue "The size of the" +
              " sliced sequence is wrong"
            //            The size of the last Page must be equal to the index of the final element (totalCount - 1)
            //            minus the index of the last element of the penultimate page (perPage * expectedLastPage - 1).

            overseers.headOption.value mustBe sortedOverseers(perPage * expectedLastPage) withClue "The first" +
              " element of the sliced sequence is wrong"

          case Left(message) => fail(message)
        }
      }
    }

    "return an empty sequence if the page is greater than the last page" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseerAddressList = genList(1, 20, genAddressRow).sample.value
      val overseerUserList = overseerAddressList.map(addressRow => genUserRow(addressRow.addressId).sample.value)
      val seqPostOverseer = overseerAddressList.map(addressRow => PostOverseer(addressRow.address, None))
      val perPage = choose(1, 20).sample.value
      val expectedLastPage = divide(overseerAddressList.size, perPage, RoundingMode.CEILING) - 1
      val page = choose(expectedLastPage + 1, expectedLastPage + 3).sample.value

      for {
        _ <- fillDB(
          addressRows = List(basicTestDB.addressRow) ++ overseerAddressList,
          userRows = List(basicTestDB.userRow) ++ overseerUserList)

        createdChatDTO <- chatsRep.postChat(genCreateChatOption.sample.value, basicTestDB.userRow.userId)

        postedOverseers <- chatsRep.postOverseers(seqPostOverseer.toSet, createdChatDTO.value.chatId.value,
          basicTestDB.userRow.userId)

        eitherResult <- chatsRep.getOverseers(createdChatDTO.value.chatId.value, page, perPage,
          DefaultOrder, basicTestDB.userRow.userId)

      } yield {
        eitherResult match {
          case Right((overseers, totalCount, _)) =>
            totalCount must be > 0
            overseers mustBe empty
          case Left(message) => fail(message)
        }
      }
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
          createdChatDTO.value.chatId.value,
          basicTestDB.userRow.userId)

        result <- chatsRep.deleteOverseer(createdChatDTO.value.chatId.value, genUUID.sample.value,
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
          createdChatDTO.value.chatId.value,
          basicTestDB.userRow.userId)

        oversightId = postedOverseers.value.headOption.value.oversightId.value

        preDeletionOversight <- db.run(OversightsTable.all.filter(_.oversightId === oversightId)
          .map(_.oversightId).result.headOption)

        result <- chatsRep.deleteOverseer(
          createdChatDTO.value.chatId.value,
          oversightId,
          basicTestDB.userRow.userId)

        postDeletionOversight <- db.run(OversightsTable.all.filter(_.oversightId === oversightId).result.headOption)

      } yield assert(result &
        preDeletionOversight === Some(oversightId) &
        postDeletionOversight === None)

    }

  }

  "SlickChatsRepository#getOversights" should {

    "return None if there is no overseeing or overseen" in {
      val basicTestDB = genBasicTestDB.sample.value
      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow),
          List(basicTestDB.emailAddressRow))

        optOversight <- chatsRep.getOversights(basicTestDB.userRow.userId)

      } yield optOversight mustBe None

    }

    "return more than one overseeing for the same chat" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseeOneAddressRow = genAddressRow.sample.value
      val overseeOneUserRow = genUserRow(overseeOneAddressRow.addressId).sample.value
      val overseeTwoAddressRow = genAddressRow.sample.value
      val overseeTwoUserRow = genUserRow(overseeTwoAddressRow.addressId).sample.value
      val overseeingRowOne = genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId,
        overseeOneUserRow.userId).sample.value
      val overseeingRowTwo = genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId,
        overseeTwoUserRow.userId).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, overseeOneAddressRow, overseeTwoAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow, overseeOneUserRow, overseeTwoUserRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow),
          List(basicTestDB.emailAddressRow),
          List(overseeingRowOne, overseeingRowTwo))

        optOversight <- chatsRep.getOversights(basicTestDB.userRow.userId)

      } yield optOversight.value mustBe Oversight(
        Some(ChatOverseeing(
          basicTestDB.chatRow.chatId,
          Set(
            Overseeing(overseeingRowOne.oversightId, overseeOneAddressRow.address),
            Overseeing(overseeingRowTwo.oversightId, overseeTwoAddressRow.address)))),
        None)
    }

    "return more than one overseen for the same chat" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseerOneAddressRow = genAddressRow.sample.value
      val overseerOneUserRow = genUserRow(overseerOneAddressRow.addressId).sample.value
      val overseerTwoAddressRow = genAddressRow.sample.value
      val overseerTwoUserRow = genUserRow(overseerTwoAddressRow.addressId).sample.value
      val overseenRowOne = genOversightRow(basicTestDB.chatRow.chatId, overseerOneUserRow.userId,
        basicTestDB.userRow.userId).sample.value
      val overseenRowTwo = genOversightRow(basicTestDB.chatRow.chatId, overseerTwoUserRow.userId,
        basicTestDB.userRow.userId).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, overseerOneAddressRow, overseerTwoAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow, overseerOneUserRow, overseerTwoUserRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow),
          List(basicTestDB.emailAddressRow),
          List(overseenRowOne, overseenRowTwo))

        optOversight <- chatsRep.getOversights(basicTestDB.userRow.userId)

      } yield optOversight.value mustBe Oversight(
        None,
        Some(ChatOverseen(
          basicTestDB.chatRow.chatId,
          Set(
            Overseen(overseenRowOne.oversightId, overseerOneAddressRow.address),
            Overseen(overseenRowTwo.oversightId, overseerTwoAddressRow.address)))))
    }

    "return overseeing for only the most recently updated chat" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseeOneAddressRow = genAddressRow.sample.value
      val overseeOneUserRow = genUserRow(overseeOneAddressRow.addressId).sample.value
      val overseeTwoAddressRow = genAddressRow.sample.value
      val overseeTwoUserRow = genUserRow(overseeTwoAddressRow.addressId).sample.value
      val chatRowTwo = genChatRow.sample.value
      val overseeingRowOne = genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId,
        overseeOneUserRow.userId)
        .sample.value
      val overseeingRowTwo = genOversightRow(chatRowTwo.chatId, basicTestDB.userRow.userId, overseeTwoUserRow.userId)
        .sample.value
      val emailTwo = genEmailRow(chatRowTwo.chatId).sample.value.copy(date = "2018")

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, overseeOneAddressRow, overseeTwoAddressRow),
          List(basicTestDB.chatRow, chatRowTwo),
          List(basicTestDB.userRow, overseeOneUserRow, overseeTwoUserRow),
          List(basicTestDB.userChatRow, genUserChatRow(basicTestDB.userRow.userId, chatRowTwo.chatId).sample.value),
          List(basicTestDB.emailRow, emailTwo),
          List(basicTestDB.emailAddressRow, genEmailAddressRow(emailTwo.emailId, chatRowTwo.chatId,
            basicTestDB.addressRow.addressId, From).sample.value),
          List(overseeingRowOne, overseeingRowTwo))

        optOversight <- chatsRep.getOversights(basicTestDB.userRow.userId)

      } yield optOversight.value mustBe Oversight(
        Some(ChatOverseeing(
          basicTestDB.chatRow.chatId,
          Set(Overseeing(overseeingRowOne.oversightId, overseeOneAddressRow.address)))),
        None)
    }

    "return overseen for only the most recently updated chat" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseerOneAddressRow = genAddressRow.sample.value
      val overseerOneUserRow = genUserRow(overseerOneAddressRow.addressId).sample.value
      val overseerTwoAddressRow = genAddressRow.sample.value
      val overseerTwoUserRow = genUserRow(overseerTwoAddressRow.addressId).sample.value
      val chatRowTwo = genChatRow.sample.value
      val overseenRowOne = genOversightRow(basicTestDB.chatRow.chatId, overseerOneUserRow.userId,
        basicTestDB.userRow.userId).sample.value
      val overseenRowTwo = genOversightRow(chatRowTwo.chatId, overseerTwoUserRow.userId, basicTestDB.userRow.userId)
        .sample.value
      val emailTwo = genEmailRow(chatRowTwo.chatId).sample.value.copy(date = "2018")

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, overseerOneAddressRow, overseerTwoAddressRow),
          List(basicTestDB.chatRow, chatRowTwo),
          List(basicTestDB.userRow, overseerOneUserRow, overseerTwoUserRow),
          List(basicTestDB.userChatRow, genUserChatRow(basicTestDB.userRow.userId, chatRowTwo.chatId).sample.value),
          List(basicTestDB.emailRow, emailTwo),
          List(basicTestDB.emailAddressRow, genEmailAddressRow(emailTwo.emailId, chatRowTwo.chatId,
            basicTestDB.addressRow.addressId, From).sample.value),
          List(overseenRowOne, overseenRowTwo))

        optOversight <- chatsRep.getOversights(basicTestDB.userRow.userId)

      } yield optOversight.value mustBe Oversight(
        None,
        Some(ChatOverseen(
          basicTestDB.chatRow.chatId,
          Set(
            Overseen(overseenRowOne.oversightId, overseerOneAddressRow.address)))))

    }

    "return both overseeing and overseen" in {
      val basicTestDB = genBasicTestDB.sample.value
      val overseerAddressRow = genAddressRow.sample.value
      val overseerUserRow = genUserRow(overseerAddressRow.addressId).sample.value
      val overseeAddressRow = genAddressRow.sample.value
      val overseeUserRow = genUserRow(overseeAddressRow.addressId).sample.value
      val overseeingRow = genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId,
        overseeUserRow.userId).sample.value
      val overseenRow = genOversightRow(basicTestDB.chatRow.chatId, overseerUserRow.userId,
        basicTestDB.userRow.userId).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, overseerAddressRow, overseeAddressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow, overseerUserRow, overseeUserRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow),
          List(basicTestDB.emailAddressRow),
          List(overseeingRow, overseenRow))

        optOversight <- chatsRep.getOversights(basicTestDB.userRow.userId)

      } yield optOversight.value mustBe Oversight(
        Some(ChatOverseeing(
          basicTestDB.chatRow.chatId,
          Set(Overseeing(overseeingRow.oversightId, overseeAddressRow.address)))),
        Some(ChatOverseen(
          basicTestDB.chatRow.chatId,
          Set(
            Overseen(overseenRow.oversightId, overseerAddressRow.address)))))
    }

  }

  "SlickChatsRepository#getOverseeings" should {

    def makeOverseeingsDB(userId: String, userAddressId: String): (FullTestDB, Int, List[ChatOverseeing]) = {
      val chats = genList(1, 20, genChatRow).sample.value
      val overseeingsDataList = chats.map(chatRow => OverseeingsData(
        chatRow,
        genList(1, 3, genOverseeingData(chatRow.chatId, userId)).sample.value,
        genUserChatVisibilityData(chatRow.chatId, userId, userAddressId)
          .sample.value))
      val perPage = choose(1, 20).sample.value
      val expectedLastPage = divide(chats.size, perPage, RoundingMode.CEILING) - 1
      val page = choose(0, expectedLastPage + 1).sample.value

      val dbAndResult = for {
        overseeingsData <- overseeingsDataList
        chatRow = overseeingsData.chatRow
        userChatVisibilityData = overseeingsData.userChatVisibilityData
        overseeingData <- overseeingsData.seqOverseeingData
      } yield ((overseeingData.overseeAddressRow, overseeingData.overseeUserRow, chatRow,
        userChatVisibilityData.userChatRow, userChatVisibilityData.emailRow, userChatVisibilityData.emailAddressRow,
        overseeingData.oversightRow),
        (
          userChatVisibilityData.emailRow.date,
          userChatVisibilityData.emailRow.body,
          ChatOverseeing(chatRow.chatId, overseeingsData.seqOverseeingData.map(overseeingData =>
            Overseeing(overseeingData.oversightRow.oversightId, overseeingData.overseeAddressRow.address)).toSet)))

      val (populateDBList, sortedOverseeings) = (
        dbAndResult.map(_._1).distinct,
        dbAndResult.map(_._2).distinct.sortBy { case (date, body, chatOverseeing) => (date, body) }(Ordering
          .Tuple2(Ordering.String.reverse, Ordering.String)).map(_._3))

      (
        FullTestDB(populateDBList.map(_._1).distinct, populateDBList.map(_._2).distinct,
          populateDBList.map(_._3).distinct, populateDBList.map(_._4).distinct, populateDBList.map(_._5).distinct,
          populateDBList.map(_._6).distinct, populateDBList.map(_._7).distinct),
          chats.size,
          sortedOverseeings)
    }

    "return None if page is less than zero" in {
      for {
        optOverseeings <- chatsRep.getOverseeings(
          choose(-10, -1).sample.value,
          choose(1, 10).sample.value, DefaultOrder, genUUID.sample.value)
      } yield optOverseeings mustBe None
    }

    "return None if perPage is not greater than zero" in {
      for {
        optOverseeings <- chatsRep.getOverseeings(
          choose(1, 10).sample.value.sample.value,
          choose(-10, 0).sample.value, DefaultOrder, genUUID.sample.value)
      } yield optOverseeings mustBe None
    }

    "return None if perPage is greater than the maximum" in {
      for {
        optOverseeing <- chatsRep.getOverseeings(
          choose(1, 10).sample.value.sample.value,
          choose(MAX_PER_PAGE + 1, MAX_PER_PAGE + 3).sample.value, DefaultOrder, genUUID.sample.value)
      } yield optOverseeing mustBe None
    }

    "return an empty sequence if there are no overseeings" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow),
          List(basicTestDB.emailAddressRow))

        optResult <- chatsRep.getOverseeings(0, 1, DefaultOrder, basicTestDB.userRow.userId)

      } yield {
        val result = optResult.value
        val seqChatOverseeing = result._1
        val totalCount = result._2
        totalCount mustBe 0 withClue "The totalCount is wrong"
        seqChatOverseeing mustBe empty
      }
    }

    "show more than one chatOverseeing in ascending order of date" in {
      val basicTestDB = genBasicTestDB.sample.value
      val oldChatRow = genChatRow.sample.value
      val oldEmailRow = genEmailRow(oldChatRow.chatId).sample.value.copy(date = "2018")
      val oldEmailAddressesRow = genEmailAddressRow(oldEmailRow.emailId, oldChatRow.chatId,
        basicTestDB.addressRow.addressId, From).sample.value
      val oldOverseeAddressRow = genAddressRow.sample.value
      val oldOverseeUserRow = genUserRow(oldOverseeAddressRow.addressId).sample.value
      val newOverseeAddressRow = genAddressRow.sample.value
      val newOverseeUserRow = genUserRow(newOverseeAddressRow.addressId).sample.value
      val newOversightRow = genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId,
        newOverseeUserRow.userId).sample.value
      val oldOversightRow = genOversightRow(oldChatRow.chatId, basicTestDB.userRow.userId,
        oldOverseeUserRow.userId).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, oldOverseeAddressRow, newOverseeAddressRow),
          List(basicTestDB.chatRow, oldChatRow),
          List(basicTestDB.userRow, oldOverseeUserRow, newOverseeUserRow),
          List(basicTestDB.userChatRow, genUserChatRow(
            basicTestDB.userRow.userId,
            oldChatRow.chatId).sample.value),
          List(basicTestDB.emailRow, oldEmailRow),
          List(
            basicTestDB.emailAddressRow,
            genEmailAddressRow(oldEmailRow.emailId, oldChatRow.chatId,
              basicTestDB.addressRow.addressId, From).sample.value),
          List(oldOversightRow, newOversightRow))

        optResult <- chatsRep.getOverseeings(DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, Asc,
          basicTestDB.userRow.userId)
      } yield optResult.value._1 mustBe Seq(
        ChatOverseeing(
          oldChatRow.chatId,
          Set(Overseeing(oldOversightRow.oversightId, oldOverseeAddressRow.address))),
        ChatOverseeing(
          basicTestDB.chatRow.chatId,
          Set(Overseeing(newOversightRow.oversightId, newOverseeAddressRow.address))))
    }

    "show more than one chatOverseeing in descending order of date" in {
      val basicTestDB = genBasicTestDB.sample.value
      val oldChatRow = genChatRow.sample.value
      val oldEmailRow = genEmailRow(oldChatRow.chatId).sample.value.copy(date = "2018")
      val oldEmailAddressesRow = genEmailAddressRow(oldEmailRow.emailId, oldChatRow.chatId,
        basicTestDB.addressRow.addressId, From).sample.value
      val oldOverseeAddressRow = genAddressRow.sample.value
      val oldOverseeUserRow = genUserRow(oldOverseeAddressRow.addressId).sample.value
      val newOverseeAddressRow = genAddressRow.sample.value
      val newOverseeUserRow = genUserRow(newOverseeAddressRow.addressId).sample.value
      val newOversightRow = genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId,
        newOverseeUserRow.userId).sample.value
      val oldOversightRow = genOversightRow(oldChatRow.chatId, basicTestDB.userRow.userId,
        oldOverseeUserRow.userId).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, oldOverseeAddressRow, newOverseeAddressRow),
          List(basicTestDB.chatRow, oldChatRow),
          List(basicTestDB.userRow, oldOverseeUserRow, newOverseeUserRow),
          List(basicTestDB.userChatRow, genUserChatRow(
            basicTestDB.userRow.userId,
            oldChatRow.chatId).sample.value),
          List(basicTestDB.emailRow, oldEmailRow),
          List(
            basicTestDB.emailAddressRow,
            genEmailAddressRow(oldEmailRow.emailId, oldChatRow.chatId,
              basicTestDB.addressRow.addressId, From).sample.value),
          List(oldOversightRow, newOversightRow))

        optResult <- chatsRep.getOverseeings(DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, Desc,
          basicTestDB.userRow.userId)
      } yield optResult.value._1 mustBe Seq(
        ChatOverseeing(
          basicTestDB.chatRow.chatId,
          Set(Overseeing(newOversightRow.oversightId, newOverseeAddressRow.address))),
        ChatOverseeing(
          oldChatRow.chatId,
          Set(Overseeing(oldOversightRow.oversightId, oldOverseeAddressRow.address))))
    }

    "return the correct totalCount and lastPage values" in {
      val basicTestDB = genBasicTestDB.sample.value
      val (fullTestDB, expectedTotalCount, sortedOverseeings) = makeOverseeingsDB(
        basicTestDB.userRow.userId,
        basicTestDB.addressRow.addressId)
      val perPage = choose(1, 20).sample.value
      val expectedLastPage = divide(expectedTotalCount, perPage, RoundingMode.CEILING) - 1
      val page = choose(0, expectedLastPage + 1).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow) ++ fullTestDB.addressRows, fullTestDB.chatRows,
          List(basicTestDB.userRow) ++ fullTestDB.userRows, fullTestDB.userChatRows,
          fullTestDB.emailRows, fullTestDB.emailAddressRows, fullTestDB.oversightRows)

        optResult <- chatsRep.getOverseeings(page, perPage, DefaultOrder, basicTestDB.userRow.userId)

      } yield {
        val result = optResult.value
        val totalCount = result._2
        val lastPage = result._3
        totalCount mustBe expectedTotalCount withClue "The totalCount is wrong"
        assert(sortedOverseeings.isDefinedAt(lastPage * perPage) &&
          !sortedOverseeings.isDefinedAt((lastPage + 1) * perPage)) withClue "The value for the lastPage is wrong"
        lastPage mustBe expectedLastPage withClue "The value for the lastPage did not equal it's expected value"
      }
    }

    "sample the overseeings according to the given intermediary page and perPage values" in {
      val basicTestDB = genBasicTestDB.sample.value
      val (fullTestDB, expectedTotalCount, sortedOverseeings) = makeOverseeingsDB(
        basicTestDB.userRow.userId,
        basicTestDB.addressRow.addressId)
      val perPage = choose(1, 20).sample.value
      val expectedLastPage = divide(expectedTotalCount, perPage, RoundingMode.CEILING) - 1
      val page = choose(0, max(expectedLastPage - 1, 0)).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow) ++ fullTestDB.addressRows, fullTestDB.chatRows,
          List(basicTestDB.userRow) ++ fullTestDB.userRows, fullTestDB.userChatRows,
          fullTestDB.emailRows, fullTestDB.emailAddressRows, fullTestDB.oversightRows)

        optResult <- chatsRep.getOverseeings(page, perPage, DefaultOrder, basicTestDB.userRow.userId)

      } yield {
        val seqChatOverseeing = optResult.value._1
        seqChatOverseeing.size mustBe min(perPage, expectedTotalCount) withClue "The size of the sliced sequence" +
          " is wrong"
        seqChatOverseeing.headOption.value mustBe sortedOverseeings(perPage * page) withClue "The first element of" +
          " the sliced sequence is wrong"
      }
    }

    "correctly sample the last page of the overseeings" in {
      val basicTestDB = genBasicTestDB.sample.value
      val (fullTestDB, expectedTotalCount, sortedOverseeings) = makeOverseeingsDB(
        basicTestDB.userRow.userId,
        basicTestDB.addressRow.addressId)
      val perPage = choose(1, 20).sample.value
      val expectedLastPage = divide(expectedTotalCount, perPage, RoundingMode.CEILING) - 1

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow) ++ fullTestDB.addressRows, fullTestDB.chatRows,
          List(basicTestDB.userRow) ++ fullTestDB.userRows, fullTestDB.userChatRows,
          fullTestDB.emailRows, fullTestDB.emailAddressRows, fullTestDB.oversightRows)

        optResult <- chatsRep.getOverseeings(expectedLastPage, perPage, DefaultOrder, basicTestDB.userRow.userId)

      } yield {
        val result = optResult.value
        val seqChatOverseeing = result._1
        val totalCount = result._2
        seqChatOverseeing.size mustBe (totalCount - 1) - (perPage * expectedLastPage - 1) withClue "The size of the" +
          " sliced sequence is wrong"
        //            The size of the last Page must be equal to the index of the final element (totalCount - 1)
        //            minus the index of the last element of the penultimate page (perPage * expectedLastPage - 1).

        seqChatOverseeing.headOption.value mustBe sortedOverseeings(perPage * expectedLastPage) withClue "The first" +
          " element of the sliced sequence is wrong"
      }
    }

    "return an empty sequence if the page is greater than the last page" in {
      val basicTestDB = genBasicTestDB.sample.value
      val (fullTestDB, expectedTotalCount, _) = makeOverseeingsDB(
        basicTestDB.userRow.userId,
        basicTestDB.addressRow.addressId)
      val perPage = choose(1, 20).sample.value
      val expectedLastPage = divide(expectedTotalCount, perPage, RoundingMode.CEILING) - 1
      val page = choose(expectedLastPage + 1, expectedLastPage + 3).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow) ++ fullTestDB.addressRows, fullTestDB.chatRows,
          List(basicTestDB.userRow) ++ fullTestDB.userRows, fullTestDB.userChatRows,
          fullTestDB.emailRows, fullTestDB.emailAddressRows, fullTestDB.oversightRows)

        optResult <- chatsRep.getOverseeings(page, perPage, DefaultOrder, basicTestDB.userRow.userId)

      } yield {
        val result = optResult.value
        val seqChatOverseeing = result._1
        val totalCount = result._2
        totalCount must be > 0
        seqChatOverseeing mustBe empty
      }
    }
  }

  "SlickChatsRepository#getOverseens" should {

    def makeOverseensDB(userId: String, userAddressId: String): (FullTestDB, Int, List[ChatOverseen]) = {
      val chats = genList(1, 20, genChatRow).sample.value
      val overseensDataList = chats.map(chatRow => OverseensData(
        chatRow,
        genList(1, 3, genOverseenData(chatRow.chatId, userId)).sample.value,
        genUserChatVisibilityData(chatRow.chatId, userId, userAddressId)
          .sample.value))
      val perPage = choose(1, 20).sample.value
      val expectedLastPage = divide(chats.size, perPage, RoundingMode.CEILING) - 1
      val page = choose(0, expectedLastPage + 1).sample.value

      val dbAndResult = for {
        overseensData <- overseensDataList
        chatRow = overseensData.chatRow
        userChatVisibilityData = overseensData.userChatVisibilityData
        overseenData <- overseensData.seqOverseenData
      } yield ((overseenData.overseerAddressRow, overseenData.overseerUserRow, chatRow,
        userChatVisibilityData.userChatRow, userChatVisibilityData.emailRow, userChatVisibilityData.emailAddressRow,
        overseenData.oversightRow),
        (
          userChatVisibilityData.emailRow.date,
          userChatVisibilityData.emailRow.body,
          ChatOverseen(chatRow.chatId, overseensData.seqOverseenData.map(overseenData =>
            Overseen(overseenData.oversightRow.oversightId, overseenData.overseerAddressRow.address)).toSet)))

      val (populateDBList, sortedOverseens) = (
        dbAndResult.map(_._1).distinct,
        dbAndResult.map(_._2).distinct.sortBy { case (date, body, chatOverseen) => (date, body) }(Ordering
          .Tuple2(Ordering.String.reverse, Ordering.String)).map(_._3))

      (
        FullTestDB(populateDBList.map(_._1).distinct, populateDBList.map(_._2).distinct,
          populateDBList.map(_._3).distinct, populateDBList.map(_._4).distinct, populateDBList.map(_._5).distinct,
          populateDBList.map(_._6).distinct, populateDBList.map(_._7).distinct),
          chats.size,
          sortedOverseens)
    }

    "return None if page is less than zero" in {
      for {
        optOverseens <- chatsRep.getOverseens(
          choose(-10, -1).sample.value,
          choose(1, 10).sample.value, DefaultOrder, genUUID.sample.value)
      } yield optOverseens mustBe None
    }

    "return None if perPage is not greater than zero" in {
      for {
        optOverseens <- chatsRep.getOverseens(
          choose(1, 10).sample.value.sample.value,
          choose(-10, 0).sample.value, DefaultOrder, genUUID.sample.value)
      } yield optOverseens mustBe None
    }

    "return None if perPage is greater than the maximum" in {
      for {
        optOverseen <- chatsRep.getOverseens(
          choose(1, 10).sample.value.sample.value,
          choose(MAX_PER_PAGE + 1, MAX_PER_PAGE + 3).sample.value, DefaultOrder, genUUID.sample.value)
      } yield optOverseen mustBe None
    }

    "return an empty sequence if there are no overseens" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          List(basicTestDB.chatRow),
          List(basicTestDB.userRow),
          List(basicTestDB.userChatRow),
          List(basicTestDB.emailRow),
          List(basicTestDB.emailAddressRow))

        optResult <- chatsRep.getOverseens(0, 1, DefaultOrder, basicTestDB.userRow.userId)

      } yield {
        val result = optResult.value
        val seqChatOverseen = result._1
        val totalCount = result._2
        totalCount mustBe 0 withClue "The totalCount is wrong"
        seqChatOverseen mustBe empty
      }
    }

    "show more than one chatOverseeing in ascending order of date" in {
      val basicTestDB = genBasicTestDB.sample.value
      val oldChatRow = genChatRow.sample.value
      val oldEmailRow = genEmailRow(oldChatRow.chatId).sample.value.copy(date = "2018")
      val oldEmailAddressesRow = genEmailAddressRow(oldEmailRow.emailId, oldChatRow.chatId,
        basicTestDB.addressRow.addressId, From).sample.value
      val oldOverseerAddressRow = genAddressRow.sample.value
      val oldOverseerUserRow = genUserRow(oldOverseerAddressRow.addressId).sample.value
      val newOverseerAddressRow = genAddressRow.sample.value
      val newOverseerUserRow = genUserRow(newOverseerAddressRow.addressId).sample.value
      val newOversightRow = genOversightRow(basicTestDB.chatRow.chatId, newOverseerUserRow.userId,
        basicTestDB.userRow.userId).sample.value
      val oldOversightRow = genOversightRow(oldChatRow.chatId, oldOverseerUserRow.userId,
        basicTestDB.userRow.userId).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, oldOverseerAddressRow, newOverseerAddressRow),
          List(basicTestDB.chatRow, oldChatRow),
          List(basicTestDB.userRow, oldOverseerUserRow, newOverseerUserRow),
          List(basicTestDB.userChatRow, genUserChatRow(
            basicTestDB.userRow.userId,
            oldChatRow.chatId).sample.value),
          List(basicTestDB.emailRow, oldEmailRow),
          List(
            basicTestDB.emailAddressRow,
            genEmailAddressRow(oldEmailRow.emailId, oldChatRow.chatId,
              basicTestDB.addressRow.addressId, From).sample.value),
          List(oldOversightRow, newOversightRow))

        optResult <- chatsRep.getOverseens(DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, Asc,
          basicTestDB.userRow.userId)
      } yield optResult.value._1 mustBe Seq(
        ChatOverseen(
          oldChatRow.chatId,
          Set(Overseen(oldOversightRow.oversightId, oldOverseerAddressRow.address))),
        ChatOverseen(
          basicTestDB.chatRow.chatId,
          Set(Overseen(newOversightRow.oversightId, newOverseerAddressRow.address))))
    }

    "show more than one chatOverseeing in descending order of date" in {
      val basicTestDB = genBasicTestDB.sample.value
      val oldChatRow = genChatRow.sample.value
      val oldEmailRow = genEmailRow(oldChatRow.chatId).sample.value.copy(date = "2018")
      val oldEmailAddressesRow = genEmailAddressRow(oldEmailRow.emailId, oldChatRow.chatId,
        basicTestDB.addressRow.addressId, From).sample.value
      val oldOverseeAddressRow = genAddressRow.sample.value
      val oldOverseeUserRow = genUserRow(oldOverseeAddressRow.addressId).sample.value
      val newOverseeAddressRow = genAddressRow.sample.value
      val newOverseeUserRow = genUserRow(newOverseeAddressRow.addressId).sample.value
      val newOversightRow = genOversightRow(basicTestDB.chatRow.chatId, basicTestDB.userRow.userId,
        newOverseeUserRow.userId).sample.value
      val oldOversightRow = genOversightRow(oldChatRow.chatId, basicTestDB.userRow.userId,
        oldOverseeUserRow.userId).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow, oldOverseeAddressRow, newOverseeAddressRow),
          List(basicTestDB.chatRow, oldChatRow),
          List(basicTestDB.userRow, oldOverseeUserRow, newOverseeUserRow),
          List(basicTestDB.userChatRow, genUserChatRow(
            basicTestDB.userRow.userId,
            oldChatRow.chatId).sample.value),
          List(basicTestDB.emailRow, oldEmailRow),
          List(
            basicTestDB.emailAddressRow,
            genEmailAddressRow(oldEmailRow.emailId, oldChatRow.chatId,
              basicTestDB.addressRow.addressId, From).sample.value),
          List(oldOversightRow, newOversightRow))

        optResult <- chatsRep.getOverseeings(DEFAULT_PAGE.value, DEFAULT_PER_PAGE.value, Desc,
          basicTestDB.userRow.userId)
      } yield optResult.value._1 mustBe Seq(
        ChatOverseeing(
          basicTestDB.chatRow.chatId,
          Set(Overseeing(newOversightRow.oversightId, newOverseeAddressRow.address))),
        ChatOverseeing(
          oldChatRow.chatId,
          Set(Overseeing(oldOversightRow.oversightId, oldOverseeAddressRow.address))))
    }

    "return the correct totalCount and lastPage values" in {
      val basicTestDB = genBasicTestDB.sample.value
      val (fullTestDB, expectedTotalCount, sortedOverseens) = makeOverseensDB(
        basicTestDB.userRow.userId,
        basicTestDB.addressRow.addressId)
      val perPage = choose(1, 20).sample.value
      val expectedLastPage = divide(expectedTotalCount, perPage, RoundingMode.CEILING) - 1
      val page = choose(0, expectedLastPage + 1).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow) ++ fullTestDB.addressRows, fullTestDB.chatRows,
          List(basicTestDB.userRow) ++ fullTestDB.userRows, fullTestDB.userChatRows,
          fullTestDB.emailRows, fullTestDB.emailAddressRows, fullTestDB.oversightRows)

        optResult <- chatsRep.getOverseens(page, perPage, DefaultOrder, basicTestDB.userRow.userId)

      } yield {
        val result = optResult.value
        val totalCount = result._2
        val lastPage = result._3
        totalCount mustBe expectedTotalCount withClue "The totalCount is wrong"
        assert(sortedOverseens.isDefinedAt(lastPage * perPage) &&
          !sortedOverseens.isDefinedAt((lastPage + 1) * perPage)) withClue "The value for the lastPage is wrong"
        lastPage mustBe expectedLastPage withClue "The value for the lastPage did not equal it's expected value"
      }
    }

    "sample the overseens according to the given intermediary page and perPage values" in {
      val basicTestDB = genBasicTestDB.sample.value
      val (fullTestDB, expectedTotalCount, sortedOverseens) = makeOverseensDB(
        basicTestDB.userRow.userId,
        basicTestDB.addressRow.addressId)
      val perPage = choose(1, 20).sample.value
      val expectedLastPage = divide(expectedTotalCount, perPage, RoundingMode.CEILING) - 1
      val page = choose(0, max(expectedLastPage - 1, 0)).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow) ++ fullTestDB.addressRows, fullTestDB.chatRows,
          List(basicTestDB.userRow) ++ fullTestDB.userRows, fullTestDB.userChatRows,
          fullTestDB.emailRows, fullTestDB.emailAddressRows, fullTestDB.oversightRows)

        optResult <- chatsRep.getOverseens(page, perPage, DefaultOrder, basicTestDB.userRow.userId)

      } yield {
        val seqChatOverseen = optResult.value._1
        seqChatOverseen.size mustBe min(perPage, expectedTotalCount) withClue "The size of the sliced sequence" +
          " is wrong"
        seqChatOverseen.headOption.value mustBe sortedOverseens(perPage * page) withClue "The first element of" +
          " the sliced sequence is wrong"
      }
    }

    "correctly sample the last page of the overseens" in {
      val basicTestDB = genBasicTestDB.sample.value
      val (fullTestDB, expectedTotalCount, sortedOverseens) = makeOverseensDB(
        basicTestDB.userRow.userId,
        basicTestDB.addressRow.addressId)
      val perPage = choose(1, 20).sample.value
      val expectedLastPage = divide(expectedTotalCount, perPage, RoundingMode.CEILING) - 1

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow) ++ fullTestDB.addressRows, fullTestDB.chatRows,
          List(basicTestDB.userRow) ++ fullTestDB.userRows, fullTestDB.userChatRows,
          fullTestDB.emailRows, fullTestDB.emailAddressRows, fullTestDB.oversightRows)

        optResult <- chatsRep.getOverseens(expectedLastPage, perPage, DefaultOrder, basicTestDB.userRow.userId)

      } yield {
        val result = optResult.value
        val seqChatOverseen = result._1
        val totalCount = result._2
        seqChatOverseen.size mustBe (totalCount - 1) - (perPage * expectedLastPage - 1) withClue "The size of the" +
          " sliced sequence is wrong"
        //            The size of the last Page must be equal to the index of the final element (totalCount - 1)
        //            minus the index of the last element of the penultimate page (perPage * expectedLastPage - 1).

        seqChatOverseen.headOption.value mustBe sortedOverseens(perPage * expectedLastPage) withClue "The first" +
          " element of the sliced sequence is wrong"
      }
    }

    "return an empty sequence if the page is greater than the last page" in {
      val basicTestDB = genBasicTestDB.sample.value
      val (fullTestDB, expectedTotalCount, _) = makeOverseensDB(
        basicTestDB.userRow.userId,
        basicTestDB.addressRow.addressId)
      val perPage = choose(1, 20).sample.value
      val expectedLastPage = divide(expectedTotalCount, perPage, RoundingMode.CEILING) - 1
      val page = choose(expectedLastPage + 1, expectedLastPage + 3).sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow) ++ fullTestDB.addressRows, fullTestDB.chatRows,
          List(basicTestDB.userRow) ++ fullTestDB.userRows, fullTestDB.userChatRows,
          fullTestDB.emailRows, fullTestDB.emailAddressRows, fullTestDB.oversightRows)

        optResult <- chatsRep.getOverseens(page, perPage, DefaultOrder, basicTestDB.userRow.userId)

      } yield {
        val result = optResult.value
        val seqChatOverseen = result._1
        val totalCount = result._2
        totalCount must be > 0
        seqChatOverseen mustBe empty
      }
    }
  }

  "SlickChatsRepository#postAttachment" should {
    "correctly add an AttachmentsRow with the uploaded file information" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))
        postChatResponse <- chatsRep.postChat(genCreateChatOption.sample.value, basicTestDB.userRow.userId)
        postEmailResponse <- chatsRep.postEmail(genUpsertEmailOption.sample.value, postChatResponse.value.chatId.value,
          basicTestDB.userRow.userId)

        filename = genString.sample.value
        contentType = genString.sample.value
        path = genString.sample.value

        postAttachmentResponse <- chatsRep.postAttachment(
          postEmailResponse.value.chatId.value, postEmailResponse.value.email.emailId.value, basicTestDB.userRow.userId,
          filename, path, Some(contentType))

        emailWithOneAttachment <- chatsRep.getEmail(postEmailResponse.value.chatId.value, postEmailResponse.value.email.emailId.value, basicTestDB.userRow.userId)
        attachmentId = emailWithOneAttachment.value.emails.headOption.value.attachments.headOption.value

        attachmentRow <- db.run(AttachmentsTable.all.filter(_.attachmentId === postAttachmentResponse.value)
          .result.headOption)

      } yield assert(emailWithOneAttachment.isDefined &
        attachmentId === postAttachmentResponse.value &
        attachmentRow.value.filename === filename &
        attachmentRow.value.path === path)
    }
  }

  "SlickChatsRepository#getAttachments" should {
    "return a set of the AttachmentInfo for that email" in {
      val basicTestDB = genBasicTestDB.sample.value

      for {
        _ <- fillDB(
          List(basicTestDB.addressRow),
          userRows = List(basicTestDB.userRow))
        postChatResponse <- chatsRep.postChat(genCreateChatOption.sample.value, basicTestDB.userRow.userId)
        postEmailResponse <- chatsRep.postEmail(genUpsertEmailOption.sample.value, postChatResponse.value.chatId.value,
          basicTestDB.userRow.userId)

        filenameOne = genString.sample.value
        filenameTwo = genString.sample.value

        attachmentOneId <- chatsRep.postAttachment(
          postEmailResponse.value.chatId.value, postEmailResponse.value.email.emailId.value, basicTestDB.userRow.userId,
          filenameOne, genString.sample.value)

        attachmentTwoId <- chatsRep.postAttachment(
          postEmailResponse.value.chatId.value, postEmailResponse.value.email.emailId.value, basicTestDB.userRow.userId,
          filenameTwo, genString.sample.value)

        setAttachments <- chatsRep.getAttachments(
          postEmailResponse.value.chatId.value,
          postEmailResponse.value.email.emailId.value, basicTestDB.userRow.userId)

      } yield assert(
        setAttachments.value.contains(AttachmentInfo(attachmentOneId, filenameOne)) &
          setAttachments.value.contains(AttachmentInfo(attachmentTwoId, filenameTwo)))
    }
  }
}

case class BasicTestDB(addressRow: AddressRow, userRow: UserRow, chatRow: ChatRow, emailRow: EmailRow,
  emailAddressRow: EmailAddressRow, userChatRow: UserChatRow)

case class OverseeingsData(chatRow: ChatRow, seqOverseeingData: Seq[OverseeingData],
  userChatVisibilityData: UserChatVisibilityData)

case class OverseeingData(overseeAddressRow: AddressRow, overseeUserRow: UserRow, oversightRow: OversightRow)

case class OverseensData(chatRow: ChatRow, seqOverseenData: Seq[OverseenData],
  userChatVisibilityData: UserChatVisibilityData)

case class OverseenData(overseerAddressRow: AddressRow, overseerUserRow: UserRow, oversightRow: OversightRow)

case class UserChatVisibilityData(emailRow: EmailRow, emailAddressRow: EmailAddressRow, userChatRow: UserChatRow)

case class FullTestDB(addressRows: Seq[AddressRow], userRows: Seq[UserRow], chatRows: Seq[ChatRow],
  userChatRows: Seq[UserChatRow], emailRows: Seq[EmailRow], emailAddressRows: Seq[EmailAddressRow],
  oversightRows: Seq[OversightRow])
