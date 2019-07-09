package controllers


import javax.inject._
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
	
	val user = 3
	
	def getChats (mailboxString: String) : Action[AnyContent] = {
		val mailbox = Mailbox(mailboxString)
		Action.async {
			chatService.getChats(mailbox, user).map(array => Ok(Json.toJson(array)))
		}
	}
	
/*	def getChats (mailbox: String) : Action[AnyContent] =
		Action.async { chatService.getChats(mailbox).map{array =>
			if(array.isEmpty) Ok(Json.toJson(array))
			else NotFound
		}
		}
	*/
	
	
}
