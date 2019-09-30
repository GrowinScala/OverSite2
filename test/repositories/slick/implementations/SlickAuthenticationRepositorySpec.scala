package repositories.slick.implementations

import java.time.LocalDateTime

import org.scalatest._
import pdi.jwt.{ JwtAlgorithm, JwtJson }
import play.api.Configuration
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import repositories.dtos.{ Jwt, UserAccess }
import repositories.slick.mappings._
import slick.jdbc.MySQLProfile.api._
import utils.DataValidators
import utils.Generators._
import utils.Jsons.{ internalError, tokenNotValid }
import utils.TestGenerators._
import play.api.libs.json._

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.util.{ Failure, Success }

class SlickAuthenticationRepositorySpec extends AsyncWordSpec
  with OptionValues with MustMatchers with BeforeAndAfterAll with BeforeAndAfterEach {

  private lazy val appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
  private lazy val injector: Injector = appBuilder.injector()
  private val db = injector.instanceOf[Database]
  private val config = injector.instanceOf[Configuration]
  implicit val ec: ExecutionContext = injector.instanceOf[ExecutionContext]
  val authenticationRep = new SlickAuthenticationRepository(config, db)

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
      val userAccess: UserAccess = genUserAccess.sample.value
      val key: String = config.get[String]("secretKey")
      val algo = JwtAlgorithm.HS256

      for {
        createdtoken <- authenticationRep.signUpUser(userAccess)

        testQuery <- db.run((for {
          addressRow <- AddressesTable.all.filter(_.address === userAccess.address)
          userRow <- UsersTable.all.filter(_.addressId === addressRow.addressId)
          passwordRow <- PasswordsTable.all.filter(_.userId === userRow.userId)
          tokenRow <- TokensTable.all.filter(_.tokenId === passwordRow.tokenId)
        } yield (addressRow.address, userRow.userId, passwordRow.password, userRow.firstName, userRow.lastName,
          tokenRow.token))
          .result.headOption)

      } yield testQuery match {
        case Some((address, userId, password, firstName, lastName, token)) =>
          assert(address === userAccess.address &&
            password === userAccess.password &&
            firstName === userAccess.first_name.value &&
            lastName === userAccess.last_name.value &&
            {
              JwtJson.decodeJson(token, key, Seq(algo)) match {
                case Success(json) => json.validate[Jwt].fold(
                  errors => fail("Failed to validate the JWT to the case class"),
                  jwt => jwt.userId === userId)
                case Failure(e) => fail("Failed to decode the token")
              }
            })

        case None => fail("Test Query failed to find user info")
      }
    }
  }

  "SlickAuthenticationRepository#getPassword" should {

    "get existing password " in {
      val userAccess: UserAccess = genUserAccess.sample.value
      for {
        _ <- authenticationRep.signUpUser(userAccess)
        optPassword <- authenticationRep.getPassword(userAccess.address)
      } yield optPassword mustBe Some(userAccess.password)
    }

    "not find missing password" in {
      authenticationRep.getPassword(genEmailAddress.sample.value)
        .map(_ mustBe None)
    }
  }

  "SlickAuthenticationRepository#updateToken" should {

    "update Token" in {
      val userAccess: UserAccess = genUserAccess.sample.value
      val key: String = config.get[String]("secretKey")
      val algo = JwtAlgorithm.HS256

      for {
        userId <- authenticationRep.signUpUser(userAccess).map(token =>
          {
            JwtJson.decodeJson(token, key, Seq(algo)) match {
              case Success(json) => json.validate[Jwt].fold(
                errors => fail("Failed to validate the JWT to the case class (1st)"),
                jwt => jwt.userId)
              case Failure(e) => fail("Failed to decode the token (1st)")
            }
          })

        tokenId <- db.run(PasswordsTable.all.filter(_.password === userAccess.password)
          .map(_.tokenId).result.headOption)

        token <- authenticationRep.updateToken(userAccess.address)

        assertion <- db.run(TokensTable.all.filter(_.tokenId === tokenId.value)
          .map(_.token).result.headOption).map {
          case Some(foundToken) => assert(
            Some(foundToken) === token &&
              {
                JwtJson.decodeJson(foundToken, key, Seq(algo)) match {
                  case Success(json) => json.validate[Jwt].fold(
                    errors => fail("Failed to validate the JWT to the case class (2nd)"),
                    jwt => jwt.userId === userId)
                  case Failure(e) => fail("Failed to decode the token (2nd)")
                }
              })
          case None => fail("Failed to find tokenId")
        }
      } yield assertion
    }

    "return None for a missing token" in {

      authenticationRep.updateToken(genString.sample.value)
        .map(_ mustBe None)

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
          TokensTable.all += TokenRow(tokenId, token),
          PasswordsTable.all += PasswordRow(passwordId, userId, password, tokenId)))

        userId <- authenticationRep.getUser(token)

        assertion <- userId mustBe userId
      } yield assertion
    }
  }

}
