package services

import javax.inject.Inject
import model.dtos.UserAccessDTO
import repositories.AuthenticationRepository
import com.github.t3hnar.bcrypt._
import play.api.libs.json.JsValue
import utils.Generators.currentTimestamp
import utils.Jsons._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthenticationService @Inject() (authenticationRep: AuthenticationRepository) {

  def signUpUser(userAccessDTO: UserAccessDTO): Future[(UserAccessDTO, Option[JsValue])] =
    authenticationRep.checkUser(userAccessDTO.address).flatMap(
      user_exists =>
        if (user_exists)
          Future.successful(userAccessDTO, Some(repeatedUser))
        else {
          val encryptedPassword = userAccessDTO.password.bcrypt
          val token = authenticationRep.signUpUser(userAccessDTO.address, encryptedPassword)
          token.map(token => (userAccessDTO.copy(token = Some(token)), None))
        })

  def signInUser(userAccessDTO: UserAccessDTO): Future[(UserAccessDTO, Option[JsValue])] = {
    authenticationRep.getPassword(userAccessDTO.address).flatMap {
      case None => Future.successful(userAccessDTO, Some(missingAddress))
      case Some(password) => if (userAccessDTO.password.isBcrypted(password)) {
        authenticationRep.updateToken(userAccessDTO.address).map(token =>
          (userAccessDTO.copy(token = Some(token)), None))
      } else Future.successful(userAccessDTO, Some(wrongPassword))
    }

  }

  def validateToken(token: String): Future[Either[JsValue, String]] = {
    authenticationRep.getTokenExpirationDate(token).flatMap {
      case None => Future.successful(Left(tokenNotValid))
      case Some(endDate) => if (endDate.before(currentTimestamp))
        Future.successful(Left(tokenExpired))
      else authenticationRep.getUser(token).map(Right(_))
    }
  }

}
