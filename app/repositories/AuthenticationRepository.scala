package repositories

import java.sql.Timestamp
import repositories.dtos.UserAccess
import scala.concurrent.Future

trait AuthenticationRepository {

  def signUpUser(userAccess: UserAccess): Future[String]

  def checkUser(address: String): Future[Boolean]

  def getPassword(address: String): Future[Option[String]]

  def updateToken(address: String): Future[Option[String]]

  def getTokenExpirationDate(token: String): Future[Option[Timestamp]]

  def getUser(token: String): Future[Option[String]]

}
