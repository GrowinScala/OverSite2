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

  //region Small inserts

  def insertAddressOLD(address: String): (String, FixedSqlAction[Int, NoStream, Effect.Write]) = {
    val addressUUID = genUUID
    (addressUUID, AddressesTable.all += AddressRow(addressUUID, address))
  }

  /*  def insertAddress(address: String) = {
    val addressUUID = genUUID
    val a = for {
      existing <- AddressesTable.all.filter(_.address === address).result.headOption

      row = existing //.map(_.copy(address = address))
        .getOrElse(AddressRow(UUID.randomUUID().toString, address))

      insert <- AddressesTable.all.insertOrUpdate(row)
      //if result == 1
    } yield (row.addressId, insert)
  }*/

  def insertUser(addressId: String): (String, FixedSqlAction[Int, NoStream, Effect.Write]) = {
    val userUUID = genUUID
    (userUUID, UsersTable.all += UserRow(userUUID, addressId, "", ""))
  }

  def upsertTokenDBIO(tokenId: String, token: String): FixedSqlAction[Int, NoStream, Effect.Write] = {
    val valid_time_24h = 24 * 60 * 60 * 1000
    val current_time = System.currentTimeMillis
    val start_date = new Timestamp(current_time)
    val end_date = new Timestamp(current_time + valid_time_24h)
    TokensTable.all.insertOrUpdate(TokenRow(tokenId, token, start_date, end_date))
  }

  /* def upsertTokenOLD(tokenId: Option[String]): (String, FixedSqlAction[Int, NoStream, Effect.Write], String) = {
    val token = genUUID
    tokenId match {
      case None =>
        val tokenUUID = genUUID
        (tokenUUID, upsertTokenDBIO(tokenUUID, token), token)

      case Some(tokenUUID) => (tokenUUID, upsertTokenDBIO(tokenUUID, token), token)

    }
  }*/

  /*  def upsertToken(optionalTokenId: Option[String]): FixedSqlAction[Int, NoStream, Effect.Write] = {
    val token = genUUID
    val tokenId = optionalTokenId.getOrElse(genUUID)

    upsertTokenDBIO(tokenId, token)
    }*/

  def insertPassword(userId: String, password: String, tokenId: String): (String, FixedSqlAction[Int, NoStream, Effect.Write]) = {
    val passwordUUID = genUUID
    (passwordUUID, PasswordsTable.all += PasswordRow(passwordUUID, userId, password, tokenId))
  }

  //endregion

  /*  def signUpUserOLD(address: String, password: String): String = {
    val addressInsertion = insertAddress(address)
    val userInsertion = insertUser(addressInsertion._1)
    val tokenInsertion = upsertToken(None)
    val passwordInsertion = insertPassword(userInsertion._1, password, tokenInsertion._1)
    val signUpUsertAction =
      DBIO.seq(addressInsertion._2, userInsertion._2, tokenInsertion._2, passwordInsertion._2)


    db.run(signUpUsertAction)
    tokenInsertion._3
  }*/

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

  def updateTokenOLD(address: String): Future[String] = {
    val futureTokenId = db.run((for {
      addressId <- AddressesTable.all.filter(_.address === address).map(_.addressId)
      userId <- UsersTable.all.filter(_.addressId === addressId).map(_.userId)
      tokenId <- PasswordsTable.all.filter(_.userId === userId).map(_.tokenId)
    } yield tokenId).result.head)

    val newToken = genUUID
    val futureTokenUpsertion = futureTokenId.map(tokenId => upsertTokenDBIO(tokenId, newToken))

    futureTokenUpsertion.map(tokenUpsertion => {
      db.run(tokenUpsertion)
      newToken
    })
  }

  def updateToken(address: String): Future[String] = {

    val updateTokenAction = for {
      tokenId <- (for {
        addressId <- AddressesTable.all.filter(_.address === address).map(_.addressId)
        userId <- UsersTable.all.filter(_.addressId === addressId).map(_.userId)
        tokenId <- PasswordsTable.all.filter(_.userId === userId).map(_.tokenId)
      } yield tokenId).result.head

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