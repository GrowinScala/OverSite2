package controllers

import javax.inject._
import model.dtos.UserAccessDTO
import play.api.libs.json.{ JsError, JsValue, Json }
import play.api.mvc._
import services.AuthenticationService
import utils.Jsons._
import Results._

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class AuthenticationController @Inject() (implicit
  val executionContext: ExecutionContext,
  cc: ControllerComponents, authenticationService: AuthenticationService)
  extends AbstractController(cc) {

  def signUpUser: Action[JsValue] =
    Action.async(parse.json) { implicit request: Request[JsValue] =>
      val jsonValue = request.body
      jsonValue.validate[UserAccessDTO].fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        userAccessDTO =>
          authenticationService.signUpUser(userAccessDTO).map {
            case Left(error) => BadRequest(error)
            case Right(jsToken) => Ok(jsToken)
          })
    }

  def signInUser: Action[JsValue] =
    Action.async(parse.json) { implicit request: Request[JsValue] =>
      val jsonValue = request.body
      jsonValue.validate[UserAccessDTO].fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        userAccessDTO =>
          authenticationService.signInUser(userAccessDTO).map {
            case Left(error) => if (error == internalError) InternalServerError
            else BadRequest(error)
            case Right(jsToken) => Ok(jsToken)
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

  override def parser: BodyParser[AnyContent] = defaultParser

  override def invokeBlock[A](
    request: Request[A],
    block: AuthenticatedUser[A] => Future[Result]): Future[Result] =
    request.headers.get("Authorization") match {
      case None => Future.successful(Unauthorized(tokenNotFound))
      case Some(token) => authenticationService.validateToken(token).flatMap {
        case Left(error) => Future.successful(Unauthorized(error))
        case Right(userId) => block(AuthenticatedUser(userId, request))
      }
    }

}

trait AuthenticatedUserAction extends ActionBuilder[AuthenticatedUser, AnyContent]

