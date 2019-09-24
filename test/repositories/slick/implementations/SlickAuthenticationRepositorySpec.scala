package repositories.slick.implementations

import org.scalatest._
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import repositories.dtos.UserAccess
import repositories.slick.mappings._
import slick.jdbc.MySQLProfile.api._
import utils.DataValidators
import utils.Generators._
import utils.TestGenerators._

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext }

class SlickAuthenticationRepositorySpec extends AsyncWordSpec
  with OptionValues with MustMatchers with BeforeAndAfterAll with BeforeAndAfterEach {

  private lazy val appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
  private lazy val injector: Injector = appBuilder.injector()
  private val db = injector.instanceOf[Database]
  implicit val ec: ExecutionContext = injector.instanceOf[ExecutionContext]
  val authenticationRep = new SlickAuthenticationRepository(db)

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
      val addressId = genUUID.sample.value
      val address = genEmailAddress.sample.value
      val userId = genUUID.sample.value

      db.run(DBIO.seq(
        AddressesTable.all += AddressRow(addressId, address),
        UsersTable.all += UserRow(userId, addressId, genString.sample.value, genString.sample.value)))

      authenticationRep.checkUser(address).map(_ mustBe true)
    }

    "detect that user doesn't exist" in {
      val addressId = genUUID.sample.value
      val address = genEmailAddress.sample.value
      db.run(AddressesTable.all += AddressRow(addressId, address))

      authenticationRep.checkUser(address).map(_ mustBe false)
      authenticationRep.checkUser(address).map(_ mustBe false)
    }

  }

  "SlickAuthenticationRepository#signUpUser" should {

    "signUp user" in {
      val userAccess: UserAccess = genUserAccess.sample.value.copy(token = None)
      Await.result(authenticationRep.signUpUser(userAccess), Duration.Inf)

      val testQuery = db.run((for {
        addressRow <- AddressesTable.all.filter(_.address === userAccess.address)
        userRow <- UsersTable.all.filter(_.addressId === addressRow.addressId)
        passwordRow <- PasswordsTable.all.filter(_.userId === userRow.userId)
        tokenRow <- TokensTable.all.filter(_.tokenId === passwordRow.tokenId)
      } yield (addressRow.address, passwordRow.password, userRow.firstName, userRow.lastName, tokenRow.token))
        .result.headOption)

      testQuery.map {
        case Some(tuple) => assert(tuple._1 === userAccess.address &&
          tuple._2 === userAccess.password && tuple._3 === userAccess.first_name.value &&
          tuple._4 === userAccess.last_name.value && DataValidators.isValidUUID(tuple._5))

        case None => fail("Test Query failed to find user info")
      }
    }
  }

  "SlickAuthenticationRepository#getPassword" should {

    "get existing password " in {
      val userAccess: UserAccess = genUserAccess.sample.value.copy(token = None)
      Await.result(authenticationRep.signUpUser(userAccess), Duration.Inf)

      authenticationRep.getPassword(userAccess.address).map(_ mustBe Some(userAccess.password))
    }

    "not find missing password" in {

      authenticationRep.getPassword(genEmailAddress.sample.value).map(_ mustBe None)
    }
  }

  "SlickAuthenticationRepository#updateToken" should {

    "update Token OLD" in {

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

    "update Token" in {
      val userAccess: UserAccess = genUserAccess.sample.value.copy(token = None)

      for {
        _ <- authenticationRep.signUpUser(userAccess)

        tokenId <- db.run(PasswordsTable.all.filter(_.password === userAccess.password)
          .map(_.tokenId).result.headOption)

        token <- authenticationRep.updateToken(userAccess.address)

        assertion <- db.run(TokensTable.all.filter(_.tokenId === tokenId.value)
          .map(_.token).result.headOption).map {
          case Some(foundToken) => foundToken mustBe token
          case None => fail("Failed to find tokenId")
        }
      } yield assertion

    }
  }

  "SlickAuthenticationRepository#getTokenExpirationDate" should {

    "get Token Expiration Date if it exists" in {
      val currentDate = currentTimestamp
      val token = genUUID.sample.value

      for {
        _ <- db.run(TokensTable.all += TokenRow(genUUID.sample.value, token, currentDate, currentDate))

        assertion <- authenticationRep.getTokenExpirationDate(token).map(_ mustBe Some(currentDate))
      } yield assertion
    }

    "detect missing Token Expiration Date" in {
      authenticationRep.getTokenExpirationDate(genUUID.sample.value).map(_ mustBe None)

    }
  }

  "SlickAuthenticationRepository#getUser" should {

    "get User" in {
      val tokenId = genUUID.sample.value
      val token = genUUID.sample.value
      val passwordId = genUUID.sample.value
      val userId = genUUID.sample.value
      val password = genString.sample.value

      for {
        _ <- db.run(DBIO.seq(
          TokensTable.all += TokenRow(tokenId, token, currentTimestamp, currentTimestamp),
          PasswordsTable.all += PasswordRow(passwordId, userId, password, tokenId)))

        userId <- authenticationRep.getUser(token)

        assertion <- userId mustBe userId
      } yield assertion
    }
  }

}
