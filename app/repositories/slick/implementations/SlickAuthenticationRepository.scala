package repositories.slick.implementations

import java.sql.Timestamp
import javax.inject.Inject
import repositories.AuthenticationRepository
import repositories.slick.mappings._
import slick.jdbc.MySQLProfile.api._
import slick.sql.FixedSqlAction
import utils.Generators._

import scala.concurrent.{ Await, ExecutionContext, Future }

class SlickAuthenticationRepository @Inject() (db: Database)(implicit executionContext: ExecutionContext)
  extends AuthenticationRepository {

  def upsertTokenDBIO(tokenId: String, token: String): FixedSqlAction[Int, NoStream, Effect.Write] = {
    val valid_time_24h = 24 * 60 * 60 * 1000
    val current_time = System.currentTimeMillis
    val start_date = new Timestamp(current_time)
    val end_date = new Timestamp(current_time + valid_time_24h)
    TokensTable.all.insertOrUpdate(TokenRow(tokenId, token, start_date, end_date))
  }

  def signUpUser(address: String, password: String): Future[String] = {
    val signUpAction = for {
      optionalAddress <- AddressesTable.all.filter(_.address === address).result.headOption
      addressUUID = genUUID
      row = optionalAddress.getOrElse(AddressRow(addressUUID, address))
      _ <- AddressesTable.all.insertOrUpdate(row)

      userUUID = genUUID
      _ <- UsersTable.all += UserRow(userUUID, row.addressId, "", "")

      token = genUUID
      tokenId = genUUID
      _ <- upsertTokenDBIO(tokenId, token)
      passwordUUID = genUUID
      _ <- PasswordsTable.all += PasswordRow(passwordUUID, userUUID, password, tokenId)
    } yield token

    db.run(signUpAction.transactionally)
  }

  def checkUser(address: String): Future[Boolean] = {
    db.run(AddressesTable.all.filter(_.address === address)
      .join(UsersTable.all).on(_.addressId === _.addressId).exists.result)
  }

  def getPassword(address: String): Future[Option[String]] = {
    db.run((for {
      addressId <- AddressesTable.all.filter(_.address === address).map(_.addressId)
      userId <- UsersTable.all.filter(_.addressId === addressId).map(_.userId)
      password <- PasswordsTable.all.filter(_.userId === userId).map(_.password)
    } yield password).result.headOption)
  }

  def updateToken(address: String): Future[String] = {

    val updateTokenAction = for {
      tokenId <- (for {
        addressId <- AddressesTable.all.filter(_.address === address).map(_.addressId)
        userId <- UsersTable.all.filter(_.addressId === addressId).map(_.userId)
        tokenId <- PasswordsTable.all.filter(_.userId === userId).map(_.tokenId)
      } yield tokenId).result.head
      // Assumes that the previous verification for the password/user/address will give a result here

      newToken = genUUID
      _ <- upsertTokenDBIO(tokenId, newToken)

    } yield newToken

    db.run(updateTokenAction.transactionally)
  }

  def getTokenExpirationDate(token: String): Future[Option[Timestamp]] =
    db.run(TokensTable.all.filter(_.token === token).map(_.endDate).result.headOption)

  def getUser(token: String): Future[String] =
    db.run((for {
      tokenId <- TokensTable.all.filter(_.token === token).map(_.tokenId)
      userId <- PasswordsTable.all.filter(_.tokenId === tokenId).map(_.userId)
    } yield userId).result.head)

}