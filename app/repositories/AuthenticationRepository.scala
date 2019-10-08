package repositories

import java.sql.Timestamp

import repositories.dtos.UserAccess
import utils.Jsons.Error

import scala.concurrent.Future

trait AuthenticationRepository {

  def signUpUser(userAccess: UserAccess): Future[String]

  def checkUser(address: String): Future[Boolean]

  def getPassword(address: String): Future[Option[String]]

  def updateToken(address: String): Future[Option[String]]

  def getUser(token: String): Future[Either[Error, String]]

}
