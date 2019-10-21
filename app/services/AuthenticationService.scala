package services

import javax.inject.Inject
import model.dtos.UserAccessDTO
import repositories.AuthenticationRepository
import com.github.t3hnar.bcrypt._
import utils.Generators.currentTimestamp
import utils.Jsons._

import scala.concurrent.{ ExecutionContext, Future }

class AuthenticationService @Inject() (implicit val ec: ExecutionContext, authenticationRep: AuthenticationRepository) {

  def signUpUser(userAccessDTO: UserAccessDTO): Future[Either[Error, JsToken]] =
    authenticationRep.checkUser(userAccessDTO.address).flatMap(
      user_exists =>
        if (user_exists)
          Future.successful(Left(repeatedUser))
        else {
          val encryptedPassword = userAccessDTO.password.bcrypt
          authenticationRep.signUpUser(UserAccessDTO.toUserAccess(userAccessDTO.copy(password = encryptedPassword)))
            .map(token => Right(jsToken(token)))
        })

  def signInUser(userAccessDTO: UserAccessDTO): Future[Either[Error, JsToken]] = {
    authenticationRep.getPassword(userAccessDTO.address).flatMap {
      case None => Future.successful(Left(failedSignIn))
      case Some(password) => if (userAccessDTO.password.isBcrypted(password)) {
        authenticationRep.updateToken(userAccessDTO.address).map {
          case Some(token) => Right(jsToken(token))
          case None => Left(internalError)
        }
      } else Future.successful(Left(failedSignIn))
    }
  }

  def validateToken(token: String): Future[Either[Error, String]] =
    authenticationRep.getUser(token)
}
