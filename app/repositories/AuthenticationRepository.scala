package repositories

import java.sql.Timestamp

import play.api.libs.json.JsValue

import scala.concurrent.Future

trait AuthenticationRepository {

  def signUpUser(address: String, password: String): Future[String]

  def checkUser(address: String): Future[Boolean]

  def getPassword(address: String): Future[Option[String]]

  def updateToken(address: String): Future[String]

  def getTokenExpirationDate(token: String): Future[Option[Timestamp]]

  def getUser(token: String): Future[String]

}
