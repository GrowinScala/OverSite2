package controllers

import javax.inject._
import model.dtos.AddressDTO
import play.api.mvc._
import play.api.libs.json.{ JsError, JsValue, Json, OFormat }
import repositories.ChatsRepository
import services.AddressService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class ChatController @Inject() (cc: ControllerComponents, chatService: ChatsRepository)
  extends AbstractController(cc) {

}
