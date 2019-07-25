package controllers

import javax.inject._
import model.dtos.PasswordDTO
import play.api.libs.json.{JsError, JsValue, Json}
import play.api.mvc._
import services.AuthenticationService

import scala.concurrent.Future

@Singleton
class AuthenticationController @Inject() (cc: ControllerComponents, authenticationService: AuthenticationService)
  extends AbstractController(cc) {

  def insertUser(): Action[JsValue] =
		Action.async(parse.json) { implicit request: Request[JsValue] =>
			val jsonValue = request.body
			jsonValue.validate[PasswordDTO].fold(
				errors => Future.successful(BadRequest(JsError.toJson(errors))),
				passwordDTO => {
					authenticationService.insertPassword(passwordDTO)
					Future.successful(Ok)
				})
		}
}

