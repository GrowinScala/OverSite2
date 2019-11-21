package services

import javax.inject.Inject
import model.dtos.UserAccessDTO
import repositories.AuthenticationRepository
import com.github.t3hnar.bcrypt._
import org.slf4j.MDC
import play.api.Logger
import utils.Jsons._
import utils.LogMessages._

import scala.concurrent.{ ExecutionContext, Future }

class AuthenticationService @Inject() (implicit val ec: ExecutionContext, authenticationRep: AuthenticationRepository) {

  private val log = Logger(this.getClass)

  def signUpUser(userAccessDTO: UserAccessDTO): Future[Either[Error, JsToken]] = {
    MDC.put("serviceMethod", "signUpUser")
    log.info(requestSignUp)
    authenticationRep.checkUser(userAccessDTO.address).flatMap(
      user_exists =>
        if (user_exists) {
          log.info(badRequest(repeatedUser))
          log.debug(s"The address ${userAccessDTO.address} was already present in the repository")
          MDC.remove("serviceMethod")
          Future.successful(Left(repeatedUser))
        } else {
          val encryptedPassword = userAccessDTO.password.bcrypt
          authenticationRep.signUpUser(UserAccessDTO.toUserAccess(userAccessDTO.copy(password = encryptedPassword)))
            .map(token => {
              log.info("The repository signed-Up the user and returned the token")
              log.debug(repToken(token))
              MDC.remove("serviceMethod")
              Right(jsToken(token))
            })
        })
  }

  def signInUser(userAccessDTO: UserAccessDTO): Future[Either[Error, JsToken]] = {
    MDC.put("serviceMethod", "signUpUser")
    log.info(requestSignIn)
    authenticationRep.getPassword(userAccessDTO.address).flatMap {
      case None =>
        log.info(badRequest(failedSignIn))
        log.debug(s"The repository did not find a correctly signed-Up User for the address ${userAccessDTO.address}")
        Future.successful(Left(failedSignIn))
      case Some(password) => if (userAccessDTO.password.isBcrypted(password)) {
        authenticationRep.updateToken(userAccessDTO.address).map {
          case Some(token) =>
            log.info("The repository signed-In the user and returned the new token")
            log.debug(repToken(token))
            Right(jsToken(token))
          case None =>
            log.error(s"The repository failed to update the token for the user: ${userAccessDTO.address}")
            Left(internalError)
        }
      } else {
        log.info(badRequest(failedSignIn))
        log.debug(s"The given password did not correspond to the one in the repository")
        Future.successful(Left(failedSignIn))
      }
    }
  }

  def validateToken(token: String): Future[Either[Error, String]] = {
    MDC.put("serviceMethod", "validateToken")
    log.info("Received request to validate a token")
    log.debug(s"Received request to validate the token: $token")
    authenticationRep.getUser(token).map(either => {
      MDC.remove("repMethod")
      MDC.remove("serviceMethod")
      either
    })
  }
}
