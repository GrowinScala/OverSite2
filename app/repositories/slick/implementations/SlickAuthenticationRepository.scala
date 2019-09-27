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

import pdi.jwt.{ JwtAlgorithm, JwtJson }
import play.api.Configuration
import play.api.libs.json.{ JsObject, Json }

import scala.concurrent.{ ExecutionContext, Future }

class SlickAuthenticationRepository @Inject() (config: Configuration, db: Database)(implicit executionContext: ExecutionContext)
  extends AuthenticationRepository {

  implicit val clock: Clock = Clock.systemUTC
  private val algo = JwtAlgorithm.HS256
  val key: String = config.get[String]("secretKey")

  /**
   * Creates a DBIOAction that inserts a new token or updates it in case it already exists
   * @param userId The Id of the user
   * @param tokenId The Id of the token
   * @return A DBIOAction that inserts a new token or updates it in case it already exists
   */
  def upsertTokenAction(userId: String, tokenId: String): DBIO[String] = {
    val startDate = LocalDateTime.now
    val endDate = startDate.plusDays(1)
    val claim = Json.obj(("userId", userId), ("startDate", startDate), ("endDate", endDate))
    val jtw = JwtJson.encode(claim, key, algo)
    TokensTable.all.insertOrUpdate(TokenRow(tokenId, jtw))
      .map(_ => jtw)
  }

  /**
   * Creates a DBIOAction that inserts a User into the Database and returns an authentication token
   * @param userAccess Contains the User's information, namely their Address, Password and Name
   * @return A DBIOAction that inserts a User into the Database and returns an authentication token
   */
  def signUpUserAction(userAccess: UserAccess): DBIO[String] =
    for {
      optionalAddress <- AddressesTable.all.filter(_.address === userAccess.address).result.headOption
      addressId <- optionalAddress match {
        case Some(addressRow) => DBIO.successful(addressRow.addressId)
        case None =>
          val addressId = newUUID
          AddressesTable.all.+=(AddressRow(addressId, userAccess.address))
            .map(_ => addressId)
      }

      userUUID = newUUID
      _ <- UsersTable.all += UserRow(userUUID, addressId, userAccess.first_name.getOrElse(""),
        userAccess.last_name.getOrElse(""))

      tokenId = newUUID
      token <- upsertTokenAction(userUUID, tokenId)
      passwordUUID = newUUID
      _ <- PasswordsTable.all += PasswordRow(passwordUUID, userUUID, userAccess.password, tokenId)
    } yield token

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
  def getPasswordAction(address: String): DBIO[Option[String]] =
    (for {
      addressId <- AddressesTable.all.filter(_.address === address).map(_.addressId)
      userId <- UsersTable.all.filter(_.addressId === addressId).map(_.userId)
      password <- PasswordsTable.all.filter(_.userId === userId).map(_.password)
    } yield password).result.headOption

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
  def updateTokenAction(address: String): DBIO[String] =
    for {
      tokenId <- (for {
        addressId <- AddressesTable.all.filter(_.address === address).map(_.addressId)
        userId <- UsersTable.all.filter(_.addressId === addressId).map(_.userId)
        tokenId <- PasswordsTable.all.filter(_.userId === userId).map(_.tokenId)
      } yield tokenId).result.head
      // Assumes that the previous verification for the password/user/address will give a result here

      newToken = newUUID
      // _ <- upsertTokenAction(tokenId, newToken)

    } yield newToken

  /**
   * Updates the token of a given address
   * @param address The address in question
   * @return The new token
   */
  def updateToken(address: String): Future[String] =
    db.run(updateTokenAction(address).transactionally)

  def getTokenExpirationDate(token: String): Future[Option[Timestamp]] =
    Future.successful(Some(new Timestamp(312)))
  //db.run(TokensTable.all.filter(_.token === token).map(_.endDate).result.headOption)

  def getUser(token: String): Future[String] =
    db.run((for {
      tokenId <- TokensTable.all.filter(_.token === token).map(_.tokenId)
      userId <- PasswordsTable.all.filter(_.tokenId === tokenId).map(_.userId)
    } yield userId).result.head)

}