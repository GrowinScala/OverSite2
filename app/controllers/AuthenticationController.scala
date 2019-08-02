package controllers

import javax.inject._
import model.dtos.UserAccessDTO
import play.api.libs.json.{ JsError, JsValue, Json }
import play.api.mvc._
import services.AuthenticationService
import utils.Jsons._

import scala.concurrent.ExecutionContext.Implicits.global
import Results._
import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }
import play.api.mvc

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class AuthenticationController @Inject() (cc: ControllerComponents, authenticationService: AuthenticationService)
  extends AbstractController(cc) {

  def signUpUser: Action[JsValue] =
    Action.async(parse.json) { implicit request: Request[JsValue] =>
      val jsonValue = request.body
      jsonValue.validate[UserAccessDTO].fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        userAccessDTO =>
          authenticationService.signUpUser(userAccessDTO).map {
            case (userAccessDto, error) => error match {
              case Some(message) => BadRequest(message)
              case None => Ok(Json.toJson(userAccessDto))
            }
          })
    }

  def signInUser: Action[JsValue] =
    Action.async(parse.json) { implicit request: Request[JsValue] =>
      val jsonValue = request.body
      jsonValue.validate[UserAccessDTO].fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        userAccessDTO =>
          authenticationService.signInUser(userAccessDTO).map {
            case (userAccessDto, error) => error match {
              case Some(message) => BadRequest(message)
              case None => Ok(Json.toJson(userAccessDto))
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

class ImplAuthenticatedUserAction @Inject() (authenticationService: AuthenticationService)
  extends AuthenticatedUserAction {

  val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: Materializer = ActorMaterializer()
  override def parser: BodyParser[AnyContent] = new mvc.BodyParsers.Default()

  override def invokeBlock[A](
    request: Request[A],
    block: AuthenticatedUser[A] => Future[Result]): Future[Result] =
    request.headers.get("token") match {
      case None => Future.successful(BadRequest(tokenNotFound))
      case Some(token) => authenticationService.validateToken(token).flatMap {
        case Left(message) => Future.successful(BadRequest(message))
        case Right(userId) => block(AuthenticatedUser(userId, request))
      }
    }

}

trait AuthenticatedUserAction extends ActionBuilder[AuthenticatedUser, AnyContent]

