package repositories.slick.implementations

import java.sql.Timestamp
import java.time.LocalDateTime

import javax.inject.Inject
import repositories.AuthenticationRepository
import repositories.dtos._
import repositories.slick.mappings._
import slick.jdbc.MySQLProfile.api._
import utils.Generators._
import java.time.Clock

import org.slf4j.MDC
import utils.Jsons._
import pdi.jwt.{ JwtAlgorithm, JwtJson }
import play.api.{ Configuration, Logger }
import play.api.libs.json.{ JsObject, Json }
import utils.LogMessages._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class SlickAuthenticationRepository @Inject() (config: Configuration, db: Database)(implicit executionContext: ExecutionContext)
  extends AuthenticationRepository {

  implicit val clock: Clock = Clock.systemUTC
  private val algo = JwtAlgorithm.HS256
  val key: String = config.get[String]("secretKey")
  private val log = Logger(this.getClass)

  /**
   * Creates a DBIOAction that inserts a new token or updates it in case it already exists
   * @param userId The Id of the user
   * @param tokenId The Id of the token
   * @return A DBIOAction that inserts a new token or updates it in case it already exists
   */
  def upsertTokenAction(userId: String, tokenId: String): DBIO[String] = {
    val claim = Json.obj(("userId", userId), ("expirationDate", LocalDateTime.now.plusDays(1)))
    val jtw = JwtJson.encode(claim, key, algo)
    TokensTable.all.insertOrUpdate(TokenRow(tokenId, jtw))
      .map(_ => jtw)
  }

  /**
   * Creates a DBIOAction that inserts a User into the Database and returns an authentication token
   * @param userAccess Contains the User's information, namely their Address, Password and Name
   * @return A DBIOAction that inserts a User into the Database and returns an authentication token
   */
  def signUpUserAction(userAccess: UserAccess): DBIO[String] = {
    MDC.put("repMethod", "signUpUserAction")
    log.info(requestSignUp)
    for {
      optionalAddress <- AddressesTable.all.filter(_.address === userAccess.address).result.headOption
      addressId <- optionalAddress match {
        case Some(addressRow) =>
          log.debug(s"The addressRow was already present: $addressRow")
          DBIO.successful(addressRow.addressId)
        case None =>
          val addressId = newUUID
          val addressRow = AddressRow(addressId, userAccess.address)
          log.debug(s"The addressRow needed to be created: $addressRow")
          AddressesTable.all.+=(addressRow)
            .map(_ => addressId)
      }

      userUUID = newUUID
      _ <- UsersTable.all += UserRow(userUUID, addressId, userAccess.first_name.getOrElse(""),
        userAccess.last_name.getOrElse(""))

      tokenId = newUUID
      token <- upsertTokenAction(userUUID, tokenId)
      passwordUUID = newUUID
      _ <- PasswordsTable.all += PasswordRow(passwordUUID, userUUID, userAccess.password, tokenId)
    } yield {
      log.info(s"User ${userAccess.address} was signed-Up")
      log.debug(s"User ${userAccess.address} was signed-Up: token: $token")
      MDC.remove("repMethod")
      token
    }
  }

  /**
   * Inserts a User into the Database and returns an authentication token
   * @param userAccess Contains the User's information, namely their Address, Password and Name
   * @return An authentication token that identifies the User
   */
  def signUpUser(userAccess: UserAccess): Future[String] =
    db.run(signUpUserAction(userAccess).transactionally)

  /**
   * Creates a DBIOAction that checks if a given address corresponds to a User
   * @param address The address in question
   * @return A DBIOAction that checks if a given address corresponds to a User
   */
  def checkUserAction(address: String): DBIO[Boolean] =
    AddressesTable.all.filter(_.address === address)
      .join(UsersTable.all).on(_.addressId === _.addressId).exists.result

  /**
   * Checks if a given address corresponds to a User
   * @param address The address in question
   * @return A boolean indicating if the address corresponds that a User
   */
  def checkUser(address: String): Future[Boolean] =
    db.run(checkUserAction(address))

  /**
   * Creates a DBIOAction that tries to find the password that corresponds to a given address
   * @param address The address in question
   * @return A DBIOAction that tries to find the password that corresponds to a given address
   */
  def getPasswordAction(address: String): DBIO[Option[String]] = {
    MDC.put("repMethod", "getPasswordAction")
    log.info(logRequest("check the presence of a user and return their password"))
    MDC.remove("repMethod")
    (for {
      addressId <- AddressesTable.all.filter(_.address === address).map(_.addressId)
      userId <- UsersTable.all.filter(_.addressId === addressId).map(_.userId)
      password <- PasswordsTable.all.filter(_.userId === userId).map(_.password)
    } yield password).result.headOption
  }

  /**
   * Tries to find the password that corresponds to a given address
   * @param address The address in question
   * @return An Option containing the password
   */
  def getPassword(address: String): Future[Option[String]] =
    db.run(getPasswordAction(address))

  /**
   * Creates a DBIOAction that updates the token of a given address
   * @param address The address in question
   * @return A DBIOAction that updates the token of a given address
   */
  def updateTokenAction(address: String): DBIO[Option[String]] = {
    MDC.put("repMethod", "updateTokenAction")
    log.info(logRequest("update a user's token"))
    log.debug(logRequest(s"update the token for $address"))
    for {
      optUserTokenId <- (for {
        addressId <- AddressesTable.all.filter(_.address === address).map(_.addressId)
        userId <- UsersTable.all.filter(_.addressId === addressId).map(_.userId)
        tokenId <- PasswordsTable.all.filter(_.userId === userId).map(_.tokenId)
      } yield (userId, tokenId)).result.headOption

      optToken <- optUserTokenId match {
        case Some((userId, tokenId)) =>
          log.info("Successfully retrieved the user's Id and tokenId")
          log.debug(s"Retrieved userId: $userId and tokenId: $tokenId for the user $address")
          upsertTokenAction(userId, tokenId).map(token => {
            log.debug(s"Update the token to $token")
            Some(token)
          })
        case None =>
          log.info("Could not find a userId and token for the requested user")
          log.debug(s"Could not find a userId and token for the user $address")
          DBIO.successful(None)
      }

    } yield {
      MDC.remove("repMethod")
      optToken
    }
  }

  /**
   * Updates the token of a given address
   * @param address The address in question
   * @return The new token
   */
  def updateToken(address: String): Future[Option[String]] =
    db.run(updateTokenAction(address).transactionally)

  def getUser(token: String): Future[Either[Error, String]] = {
    MDC.put("repMethod", "getUser")
    log.info(logRequest("get the Id of a user"))
    log.debug(logRequest(s"get the Id of the user with token: $token"))
    JwtJson.decodeJson(token, key, Seq(algo)) match {
      case Success(json) =>
        log.info("Successfully decoded the token")
        log.debug(s"Successfully decoded the token to $json")
        json.validate[Jwt].fold(
          errors => {
            log.info("The obtained json was not valid")
            log.debug(s"The obtained json was not valid: $errors")
            Future.successful(Left(tokenNotValid))
          },
          jwt => if (jwt.expirationDate.isAfter(LocalDateTime.now))
            db.run(TokensTable.all.filter(_.token === token).exists.result).map(
            if (_) {
              log.info("Found the token in the database")
              log.debug(s"Found the token in the database userId: ${jwt.userId}")
              Right(jwt.userId)
            } else {
              log.info("Did not find the token in the database")
              log.debug(s"Did not find the token: $token in the database")
              Left(tokenNotValid)
            })
          else {
            log.info("The token has expired")
            log.debug(s"The token has expired. Expiration Date: ${jwt.expirationDate}")
            Future.successful(Left(tokenNotValid))
          })
      case Failure(e) =>
        log.info("Failed to decode the token")
        log.debug(s"Failed to decode the token: ${e.toString}")
        Future.successful(Left(tokenNotValid))
    }
  }
}