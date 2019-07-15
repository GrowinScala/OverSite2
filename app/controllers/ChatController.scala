package controllers

import javax.inject._
import model.dtos.{AddressDTO, ChatDTO, EmailDTO, OverseersDTO}
import model.dtos.ChatPreviewDTO
import play.api.mvc._
import play.api.libs.json.{JsError, JsValue, Json, OFormat}
import services.{AddressService, ChatService}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import model.types.Mailbox

@Singleton
class ChatController @Inject()(cc: ControllerComponents, chatService: ChatService)
	extends AbstractController(cc) {

	def getChat(id : Int) : Action[AnyContent] =
		Action.async {
			// TODO Hard-coded userId = 1
			val userId = 1

			chatService.getChat(id, userId).map{emailDTO =>
				Ok(Json.toJson(emailDTO))
			}
		}

  val user = 3

  def getChats(mailboxString: String): Action[AnyContent] = {
    val mailbox = Mailbox(mailboxString)
    Action.async {
      chatService.getChats(mailbox, user).map(seq => Ok(Json.toJson(seq)))
    }
  }
}
