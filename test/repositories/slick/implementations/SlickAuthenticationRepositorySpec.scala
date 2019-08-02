package repositories.slick.implementations

import model.types.Mailbox.{ Drafts, Inbox }
import org.scalatest._
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import repositories.dtos.ChatPreview
import repositories.slick.mappings._
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, ExecutionContextExecutor, Future }

class SlickAuthenticationRepositorySpec extends AsyncWordSpec with MustMatchers with BeforeAndAfterAll with BeforeAndAfterEach {

  private lazy val appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
  private lazy val injector: Injector = appBuilder.injector()
  private val db = injector.instanceOf[Database]
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  //region Befores and Afters

  override def beforeAll(): Unit = {
    Await.result(db.run(DBIO.seq(
      AddressesTable.all.schema.create,
      UsersTable.all.schema.create)), Duration.Inf)
  }

  override def beforeEach(): Unit = {}

  override def afterEach(): Unit = {
    Await.result(db.run(DBIO.seq(
      AddressesTable.all.delete,
      UsersTable.all.delete)), Duration.Inf)
  }

  override def afterAll(): Unit = {
    Await.result(db.run(DBIO.seq(
      AddressesTable.all.schema.drop,
      UsersTable.all.schema.dropIfExists)), Duration.Inf)
  }

  //endregion

  "SlickAuthenticationRepository#checkUser" should {

    "detect that user exists" in {
      val authenticationRep = new SlickAuthenticationRepository(db)
      db.run(DBIO.seq(
        AddressesTable.all += AddressRow("addressId", "address"),
        UsersTable.all += UserRow("userId", "addressId", "", "")))

      authenticationRep.checkUser("address").map(_ mustBe true)
    }
  
	  
    "detect that user doesn't exists" in {
      val authenticationRep = new SlickAuthenticationRepository(db)
      db.run(AddressesTable.all += AddressRow("addressId", "address"))

      authenticationRep.checkUser("address").map(_ mustBe false)
    }
	  
  }
	
	"SlickAuthenticationRepository#signUpUser" should {
		
		"detect that user exists" in {
			val authenticationRep = new SlickAuthenticationRepository(db)
			db.run(DBIO.seq(
				AddressesTable.all += AddressRow("addressId", "address"),
				UsersTable.all += UserRow("userId", "addressId", "", "")))
			
			authenticationRep.checkUser("address").map(_ mustBe true)
		}
		
	}

}
