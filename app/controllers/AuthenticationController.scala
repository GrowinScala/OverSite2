package controllers

import javax.inject._
import model.dtos.UserAccessDTO
import play.api.libs.json.{ JsError, JsValue, Json }
import play.api.mvc._
import services.AuthenticationService
import utils.Jsons._
import Results._
import org.slf4j.MDC
import play.api.Logger
import utils.LogMessages._
import utils.Headers._

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class AuthenticationController @Inject() (implicit
  val executionContext: ExecutionContext,
  cc: ControllerComponents, authenticationService: AuthenticationService)
  extends AbstractController(cc) {

  private val log = Logger(this.getClass)

  def signUpUser: Action[JsValue] =
    Action.async(parse.json) { implicit request: Request[JsValue] =>
      MDC.put("controllerMethod", "signUpUser")
      //log.debug(receivedJson(request))
      log.info(requestSignUp)
      val jsonValue = request.body
      jsonValue.validate[UserAccessDTO].fold(
        errors => {
          log.info(invalidJson(UserAccessDTO))
          Future.successful(BadRequest)
        },
        userAccessDTO => {
          log.debug(s"User to sign-Up: ${userAccessDTO.address.toString}")
          authenticationService.signUpUser(userAccessDTO).map {
            case Left(error) =>
              log.info(badRequest(error))
              BadRequest(error)
            case Right(jsToken) =>
              log.info(s"The user successfully signed-Up: ${userAccessDTO.address}")
              log.debug(s"The user ${userAccessDTO.address.toString} signed-Up and received the token: ${
                jsToken.toString
              }")
              Ok(jsToken)
          }
        })
    }

  def signInUser: Action[JsValue] =
    Action.async(parse.json) { implicit request: Request[JsValue] =>
      MDC.put("controllerMethod", "signInUser")
      log.info(requestSignIn)
      val jsonValue = request.body
      jsonValue.validate[UserAccessDTO].fold(
        errors => {
          log.info(invalidJson(UserAccessDTO))
          Future.successful(BadRequest)
        },
        userAccessDTO => {
          log.debug(s"User to sign-In: ${userAccessDTO.address.toString}")
          authenticationService.signInUser(userAccessDTO).map {
            case Left(error) => if (error == internalError) {
              log.error("Internal Error received from service")
              InternalServerError
            } else {
              log.info(badRequest(error))
              BadRequest(error)
            }
            case Right(jsToken) =>
              log.info(s"The user successfully signed-In: ${userAccessDTO.address.toString}")
              log.debug(s"The user ${userAccessDTO.address.toString} signed-In and received the token: ${
                jsToken.toString
              }")
              Ok(jsToken)
          }
        })
    }
}

case class AuthenticatedUser[A](userId: String, request: Request[A]) extends WrappedRequest(request) {
  override def newWrapper[B](newRequest: Request[B]): AuthenticatedUser[B] =
    AuthenticatedUser(
      userId,
      super.newWrapper(newRequest))
}

class ImplAuthenticatedUserAction @Inject() (
  implicit
  val executionContext: ExecutionContext,
  defaultParser: BodyParsers.Default,
  authenticationService: AuthenticationService)
  extends AuthenticatedUserAction {

  private val log = Logger(this.getClass)

  override def parser: BodyParser[AnyContent] = defaultParser

  override def invokeBlock[A](
    request: Request[A],
    block: AuthenticatedUser[A] => Future[Result]): Future[Result] = {
    MDC.put("controllerMethod", "signUpUser")
    log.info("Received request to authenticate user")
    request.headers.get(auth) match {
      case None =>
        log.info(badRequest(tokenNotFound))
        Future.successful(BadRequest(tokenNotFound))
      case Some(token) => authenticationService.validateToken(token).flatMap {
        case Left(error) =>
          log.info(badRequest(error))
          Future.successful(BadRequest(error))
        case Right(userId) =>
          log.info("Successfully received the userId from the service")
          log.debug(s"Successfully received the userId: $userId from the service")
          block(AuthenticatedUser(userId, request))
      }
    }
  }

}

trait AuthenticatedUserAction extends ActionBuilder[AuthenticatedUser, AnyContent]

