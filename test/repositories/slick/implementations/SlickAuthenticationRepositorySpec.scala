package repositories.slick.implementations

import model.dtos.UserAccessDTO
import org.scalatest._
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import repositories.slick.mappings._
import slick.jdbc.MySQLProfile.api._
import utils.DataValidators
import utils.Generators._

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
      UsersTable.all.schema.create,
      PasswordsTable.all.schema.create,
      TokensTable.all.schema.create)), Duration.Inf)
  }

  override def beforeEach(): Unit = {}

  override def afterEach(): Unit = {
    Await.result(db.run(DBIO.seq(
      AddressesTable.all.delete,
      UsersTable.all.delete,
      PasswordsTable.all.delete,
      TokensTable.all.delete)), Duration.Inf)
  }

  override def afterAll(): Unit = {
    Await.result(db.run(DBIO.seq(
      AddressesTable.all.schema.drop,
      UsersTable.all.schema.drop,
      PasswordsTable.all.schema.drop,
      TokensTable.all.schema.drop)), Duration.Inf)
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
      authenticationRep.checkUser("address").map(_ mustBe false)
    }

  }

  "SlickAuthenticationRepository#signUpUser" should {

    "signUp user" in {
      val authenticationRep = new SlickAuthenticationRepository(db)
      val userAccessDTO: UserAccessDTO = UserAccessDTO("test@mail.com", "12345", Some("John"), Some("Doe"), None)
      Await.result(authenticationRep.signUpUser(userAccessDTO), Duration.Inf)

      val testQuery = db.run((for {
        addressRow <- AddressesTable.all.filter(_.address === userAccessDTO.address)
        userRow <- UsersTable.all.filter(_.addressId === addressRow.addressId)
        passwordRow <- PasswordsTable.all.filter(_.userId === userRow.userId)
        tokenRow <- TokensTable.all.filter(_.tokenId === passwordRow.tokenId)
      } yield (addressRow.address, passwordRow.password, userRow.firstName, userRow.lastName, tokenRow.token))
        .result.headOption)

      testQuery.map {
        case Some(tuple) => assert(tuple._1 === userAccessDTO.address &&
          tuple._2 === userAccessDTO.password && tuple._3 === userAccessDTO.first_name.get &&
          tuple._4 === userAccessDTO.last_name.get && DataValidators.isValidUUID(tuple._5))

        case None => fail("Test Query failed to find user info")
      }

    }

  }

  "SlickAuthenticationRepository#getPassword" should {

    "get existing password " in {
      val authenticationRep = new SlickAuthenticationRepository(db)
      Await.result(db.run(DBIO.seq(
        AddressesTable.all += AddressRow("addressId", "address"),
        UsersTable.all += UserRow("userId", "addressId", "", ""),
        TokensTable.all += TokenRow("tokenId", "token", currentTimestamp, currentTimestamp),
        PasswordsTable.all += PasswordRow("passwordId", "userId", "password", "tokenId"))), Duration.Inf)

      authenticationRep.getPassword("address").map(_ mustBe Some("password"))
    }

    "not find missing password" in {
      val authenticationRep = new SlickAuthenticationRepository(db)

      authenticationRep.getPassword("address").map(_ mustBe None)
    }
  }

  "SlickAuthenticationRepository#updateToken" should {

    "update Token" in {
      val authenticationRep = new SlickAuthenticationRepository(db)

      for {
        _ <- db.run(DBIO.seq(
          AddressesTable.all += AddressRow("addressId", "address"),
          UsersTable.all += UserRow("userId", "addressId", "", ""),
          TokensTable.all += TokenRow("tokenId", "token", currentTimestamp, currentTimestamp),
          PasswordsTable.all += PasswordRow("passwordId", "userId", "password", "tokenId")))

        token <- authenticationRep.updateToken("address")

        assertion <- db.run(TokensTable.all.filter(_.tokenId === "tokenId").map(_.token).result.headOption).map {
          case Some(foundToken) => foundToken mustBe token
          case None => fail("Failed to find tokenId")
        }
      } yield assertion

    }
  }

  "SlickAuthenticationRepository#getTokenExpirationDate" should {

    "get Token Expiration Date if it exists" in {
      val authenticationRep = new SlickAuthenticationRepository(db)
      val currentDate = currentTimestamp

      for {
        _ <- db.run(TokensTable.all += TokenRow("tokenId", "token", currentDate, currentDate))

        assertion <- authenticationRep.getTokenExpirationDate("token").map(_ mustBe Some(currentDate))
      } yield assertion
    }

    "detect missing Token Expiration Date" in {
      val authenticationRep = new SlickAuthenticationRepository(db)
      authenticationRep.getTokenExpirationDate("token").map(_ mustBe None)

    }
  }

  "SlickAuthenticationRepository#getUser" should {

    "get User" in {
      val authenticationRep = new SlickAuthenticationRepository(db)

      for {
        _ <- db.run(DBIO.seq(
          TokensTable.all += TokenRow("tokenId", "token", currentTimestamp, currentTimestamp),
          PasswordsTable.all += PasswordRow("passwordId", "userId", "password", "tokenId")))

        userId <- authenticationRep.getUser("token")

        assertion <- userId mustBe "userId"
      } yield assertion
    }
  }

}
