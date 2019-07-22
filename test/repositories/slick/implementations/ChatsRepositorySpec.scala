package repositories.slick.implementations

import org.scalatest._
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import repositories.slick.mappings._
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, ExecutionContextExecutor, Future }

class ChatsRepositorySpec extends AsyncWordSpec with MustMatchers with BeforeAndAfterAll with BeforeAndAfterEach {

  private lazy val appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
  private lazy val injector: Injector = appBuilder.injector()
  private val db = injector.instanceOf[Database]
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

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
      AttachmentsTable.all.schema.create)), Duration.Inf)
  }

  override def beforeEach(): Unit = {
    Await.result(db.run(DBIO.seq(
      AddressesTable.all ++= Seq(
        AddressRow(1, "beatriz@mail.com"),
        AddressRow(2, "joao@mail.com"),
        AddressRow(3, "valter@mail.com"),
        AddressRow(4, "pedrol@mail.com"),
        AddressRow(5, "pedroc@mail.com"),
        AddressRow(6, "rui@mail.com"),
        AddressRow(7, "margarida@mail.com"),
        AddressRow(8, "ricardo@mail.com"),
        AddressRow(9, "ivo@mail.com"),
        AddressRow(10, "joana@mail.com"),
        AddressRow(11, "daniel@mail.com")),
      UsersTable.all ++= Seq(
        UserRow(1, 1, "Beatriz", "Santos"),
        UserRow(2, 2, "João", "Simões"),
        UserRow(3, 3, "Valter", "Fernandes"),
        UserRow(4, 4, "Pedro", "Luís"),
        UserRow(5, 5, "Pedro", "Correia"),
        UserRow(6, 6, "Rui", "Valente")),
      ChatsTable.all ++= Seq(
        ChatRow(1, "Projeto Oversite2"),
        ChatRow(2, "Laser Tag Quarta-feira"),
        ChatRow(3, "Vencimento")),
      EmailsTable.all ++= Seq(
        EmailRow(1, 1, "Olá Beatriz e João! Vamos começar o projeto.", "2019-06-17 10:00:00", 1),
        EmailRow(2, 1, "Okay! Onde nos reunimos?", "2019-06-17 10:01:00", 1),
        EmailRow(3, 1, "Scrum room", "2019-06-17 10:02:00", 1),
        EmailRow(4, 1, "Valter, tive um imprevisto. Chego às 10h30", "2019-06-17 10:03:00", 1),
        EmailRow(5, 1, "Okay, não há problema.", "2019-06-17 10:04:00", 1),
        EmailRow(6, 1, "Estou a chegar!", "2019-06-17 10:05:00", 0),
        EmailRow(7, 2, "Vamos ao laser tag na quarta?", "2019-06-19 11:00:00", 1),
        EmailRow(8, 2, "Bora!", "2019-06-19 11:01:00", 1),
        EmailRow(9, 2, "Valter, não posso...", "2019-06-19 11:02:00", 1),
        EmailRow(10, 2, "A que horas?", "2019-06-19 11:03:00", 1),
        EmailRow(11, 2, "18h00", "2019-06-19 11:04:00", 1),
        EmailRow(12, 2, "Também vou!", "2019-06-19 11:05:00", 0),
        EmailRow(13, 2, "Talvez vá", "2019-06-19 11:06:00", 0),
        EmailRow(14, 3, "Olá Beatriz e João! Já receberam o vosso vencimento?", "2019-06-27 11:00:00", 1),
        EmailRow(15, 3, "Sim!", "2019-06-27 11:01:00", 1),
        EmailRow(16, 3, "Não...", "2019-06-27 11:02:00", 1),
        EmailRow(17, 3, "Já vou resolver o assunto!", "2019-06-27 11:03:00", 1),
        EmailRow(18, 3, "Okay, obrigada!", "2019-06-27 11:04:00", 0)),
      EmailAddressesTable.all ++= Seq(
        EmailAddressRow(1, 1, 1, 3, "from"),
        EmailAddressRow(2, 1, 1, 1, "to"),
        EmailAddressRow(3, 1, 1, 2, "to"),
        EmailAddressRow(4, 2, 1, 2, "from"),
        EmailAddressRow(5, 2, 1, 1, "to"),
        EmailAddressRow(6, 2, 1, 3, "to"),
        EmailAddressRow(7, 3, 1, 3, "from"),
        EmailAddressRow(8, 3, 1, 1, "to"),
        EmailAddressRow(9, 3, 1, 2, "to"),
        EmailAddressRow(10, 4, 1, 1, "from"),
        EmailAddressRow(11, 4, 1, 3, "to"),
        EmailAddressRow(12, 5, 1, 3, "from"),
        EmailAddressRow(13, 5, 1, 1, "to"),
        EmailAddressRow(14, 6, 1, 1, "from"),
        EmailAddressRow(15, 6, 1, 3, "to"),
        EmailAddressRow(16, 7, 2, 3, "from"),
        EmailAddressRow(17, 7, 2, 1, "to"),
        EmailAddressRow(18, 7, 2, 2, "to"),
        EmailAddressRow(19, 7, 2, 4, "to"),
        EmailAddressRow(20, 7, 2, 5, "to"),
        EmailAddressRow(21, 7, 2, 6, "to"),
        EmailAddressRow(22, 7, 2, 7, "to"),
        EmailAddressRow(23, 7, 2, 8, "to"),
        EmailAddressRow(24, 7, 2, 9, "to"),
        EmailAddressRow(25, 7, 2, 10, "cc"),
        EmailAddressRow(26, 7, 2, 11, "bcc"),
        EmailAddressRow(27, 8, 2, 7, "from"),
        EmailAddressRow(28, 8, 2, 1, "to"),
        EmailAddressRow(29, 8, 2, 2, "to"),
        EmailAddressRow(30, 8, 2, 3, "to"),
        EmailAddressRow(31, 8, 2, 4, "to"),
        EmailAddressRow(32, 8, 2, 5, "to"),
        EmailAddressRow(33, 8, 2, 6, "to"),
        EmailAddressRow(34, 8, 2, 8, "to"),
        EmailAddressRow(35, 8, 2, 9, "to"),
        EmailAddressRow(36, 9, 2, 1, "from"),
        EmailAddressRow(37, 9, 2, 3, "to"),
        EmailAddressRow(38, 10, 2, 2, "from"),
        EmailAddressRow(39, 10, 2, 1, "to"),
        EmailAddressRow(40, 10, 2, 3, "to"),
        EmailAddressRow(41, 10, 2, 4, "to"),
        EmailAddressRow(42, 10, 2, 5, "to"),
        EmailAddressRow(43, 10, 2, 6, "to"),
        EmailAddressRow(44, 10, 2, 7, "to"),
        EmailAddressRow(45, 10, 2, 8, "to"),
        EmailAddressRow(46, 10, 2, 9, "to"),
        EmailAddressRow(47, 11, 2, 3, "from"),
        EmailAddressRow(48, 11, 2, 1, "to"),
        EmailAddressRow(49, 11, 2, 2, "to"),
        EmailAddressRow(50, 11, 2, 4, "to"),
        EmailAddressRow(51, 11, 2, 5, "to"),
        EmailAddressRow(52, 11, 2, 6, "to"),
        EmailAddressRow(53, 11, 2, 7, "to"),
        EmailAddressRow(54, 11, 2, 8, "to"),
        EmailAddressRow(55, 11, 2, 9, "to"),
        EmailAddressRow(56, 12, 2, 4, "from"),
        EmailAddressRow(57, 12, 2, 1, "to"),
        EmailAddressRow(58, 12, 2, 2, "to"),
        EmailAddressRow(59, 12, 2, 3, "to"),
        EmailAddressRow(60, 12, 2, 5, "to"),
        EmailAddressRow(61, 12, 2, 6, "to"),
        EmailAddressRow(62, 12, 2, 7, "to"),
        EmailAddressRow(63, 12, 2, 8, "to"),
        EmailAddressRow(64, 12, 2, 9, "to"),
        EmailAddressRow(65, 13, 2, 5, "from"),
        EmailAddressRow(66, 13, 2, 3, "to"),
        EmailAddressRow(67, 14, 3, 10, "from"),
        EmailAddressRow(68, 14, 3, 1, "to"),
        EmailAddressRow(69, 14, 3, 2, "to"),
        EmailAddressRow(70, 15, 3, 2, "from"),
        EmailAddressRow(71, 15, 3, 10, "to"),
        EmailAddressRow(72, 16, 3, 1, "from"),
        EmailAddressRow(73, 16, 3, 10, "to"),
        EmailAddressRow(74, 17, 3, 10, "from"),
        EmailAddressRow(75, 17, 3, 1, "to"),
        EmailAddressRow(76, 18, 3, 1, "from"),
        EmailAddressRow(77, 18, 3, 10, "to")),
      AttachmentsTable.all ++= Seq(
        AttachmentRow(1, 1),
        AttachmentRow(2, 1),
        AttachmentRow(3, 1),
        AttachmentRow(4, 7),
        AttachmentRow(5, 15)),
      UserChatsTable.all ++= Seq(
        UserChatRow(1, 1, 1, 1, 1, 1, 0),
        UserChatRow(2, 2, 1, 1, 1, 0, 0),
        UserChatRow(3, 3, 1, 1, 1, 0, 0),
        UserChatRow(4, 4, 1, 1, 0, 0, 0),
        UserChatRow(5, 5, 1, 1, 0, 0, 0),
        UserChatRow(6, 6, 1, 1, 0, 0, 0),
        UserChatRow(7, 1, 2, 1, 1, 0, 0),
        UserChatRow(8, 2, 2, 1, 0, 1, 0),
        UserChatRow(9, 3, 2, 1, 1, 0, 0),
        UserChatRow(10, 4, 2, 1, 1, 1, 0),
        UserChatRow(11, 5, 2, 1, 1, 1, 0),
        UserChatRow(12, 6, 2, 1, 0, 0, 0),
        UserChatRow(13, 1, 3, 1, 1, 1, 0),
        UserChatRow(14, 2, 3, 1, 1, 0, 0),
        UserChatRow(15, 3, 3, 1, 0, 0, 0),
        UserChatRow(16, 4, 3, 1, 0, 0, 0)),
      OversightsTable.all ++= Seq(
        OversightRow(1, 1, 4, 3),
        OversightRow(2, 1, 5, 3),
        OversightRow(3, 1, 6, 3),
        OversightRow(4, 2, 2, 1),
        OversightRow(5, 2, 4, 1),
        OversightRow(6, 3, 3, 1),
        OversightRow(7, 3, 3, 2),
        OversightRow(8, 3, 4, 1)))), Duration.Inf)
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
      AttachmentsTable.all.delete)), Duration.Inf)
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

  "SlickChatsRepository#getChatData" should {
    "return Id and Subject" in {
      val chatsRep = new SlickChatsRepository(db)
      val chatData = chatsRep.getChatData(1)

      chatData.map(_ mustBe Some(1, "Projeto Oversite2"))
    }
  }

}
