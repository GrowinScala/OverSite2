package repositories.slick.implementations

import java.sql.Timestamp

import javax.inject.Inject
import model.dtos.UserAccessDTO
import repositories.AuthenticationRepository
import repositories.slick.mappings._
import slick.jdbc.MySQLProfile.api._
import utils.Generators._

import scala.concurrent.{ Await, ExecutionContext, Future }

class SlickAuthenticationRepository @Inject() (db: Database)(implicit executionContext: ExecutionContext)
  extends AuthenticationRepository {
  
  /**
    * Creates a DBIOAction that inserts a new token or updates it in case it already exists
    * @param tokenId The Id of the token
    * @param token The token
    * @return A DBIOAction that inserts a new token or updates it in case it already exists
    */
  def upsertTokenAction(tokenId: String, token: String)= {
    val valid_time_24h = 24 * 60 * 60 * 1000
    val current_time = System.currentTimeMillis
    val start_date = new Timestamp(current_time)
    val end_date = new Timestamp(current_time + valid_time_24h)
    TokensTable.all.insertOrUpdate(TokenRow(tokenId, token, start_date, end_date))
  }
  
  /**
    * Creates a DBIOAction that inserts a User into the Database and returns an authentication token
    * @param userAccessDTO Contains the User's information, namely their Address, Password and Name
    * @return A DBIOAction that inserts a User into the Database and returns an authentication token
    */
  def signUpUserAction(userAccessDTO: UserAccessDTO) =
    for {
      optionalAddress <- AddressesTable.all.filter(_.address === userAccessDTO.address).result.headOption
      addressUUID = genUUID
      row = optionalAddress.getOrElse(AddressRow(addressUUID, userAccessDTO.address))
      _ <- AddressesTable.all.insertOrUpdate(row)
      
      userUUID = genUUID
      _ <- UsersTable.all += UserRow(userUUID, row.addressId, userAccessDTO.first_name.getOrElse(""),
        userAccessDTO.last_name.getOrElse(""))
      
      token = genUUID
      tokenId = genUUID
      _ <- upsertTokenAction(tokenId, token)
      passwordUUID = genUUID
      _ <- PasswordsTable.all += PasswordRow(passwordUUID, userUUID, userAccessDTO.password, tokenId)
    } yield token
  
  
  /**
    * Inserts a User into the Database and returns an authentication token
    * @param userAccessDTO Contains the User's information, namely their Address, Password and Name
    * @return An authentication token that identifies the User
    */
  def signUpUser(userAccessDTO: UserAccessDTO): Future[String] =
    db.run(signUpUserAction(userAccessDTO).transactionally)
  
  
  /**
    * Creates a DBIOAction that checks if a given address corresponds to a User
    * @param address The address in question
    * @return A DBIOAction that checks if a given address corresponds to a User
    */
  def checkUserAction(address: String) =
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
  def getPasswordAction(address: String) =
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
  def updateTokenAction(address: String)=
    for {
      tokenId <- (for {
        addressId <- AddressesTable.all.filter(_.address === address).map(_.addressId)
        userId <- UsersTable.all.filter(_.addressId === addressId).map(_.userId)
        tokenId <- PasswordsTable.all.filter(_.userId === userId).map(_.tokenId)
      } yield tokenId).result.head
      // Assumes that the previous verification for the password/user/address will give a result here
      
      newToken = genUUID
      _ <- upsertTokenAction(tokenId, newToken)
      
    } yield newToken
  
  
  /**
    * Updates the token of a given address
    * @param address The address in question
    * @return The new token
    */
  def updateToken(address: String): Future[String] =
    db.run(updateTokenAction(address).transactionally)
  

  def getTokenExpirationDate(token: String): Future[Option[Timestamp]] =
    db.run(TokensTable.all.filter(_.token === token).map(_.endDate).result.headOption)

  def getUser(token: String): Future[String] =
    db.run((for {
      tokenId <- TokensTable.all.filter(_.token === token).map(_.tokenId)
      userId <- PasswordsTable.all.filter(_.tokenId === tokenId).map(_.userId)
    } yield userId).result.head)

}