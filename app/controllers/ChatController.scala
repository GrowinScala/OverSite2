package controllers

import javax.inject._
import model.dtos.{ AddressDTO, ChatDTO, EmailDTO, OverseersDTO }
import model.dtos.ChatPreviewDTO
import play.api.mvc._
import play.api.libs.json.{ JsError, JsValue, Json, OFormat }
import services.{ AddressService, ChatService, ChatServiceImpl }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import model.types.Mailbox
import validations.CategoryNames

@Singleton
class ChatController @Inject() (cc: ControllerComponents, chatService: ChatService)
  extends AbstractController(cc) {

  def getChat(id: Int): Action[AnyContent] =
    Action.async {
      val userId = 4

      chatService.getChat(id, userId).map {
        case Some(chat) => Ok(Json.toJson(chat))
        case None => NotFound
      }
    }

  def getChats(mailboxString: String): Action[AnyContent] = {
    Action.async {
      if (CategoryNames.validMailboxes.contains(mailboxString)) {
        val mailbox = Mailbox(mailboxString)
        val user = 2
        chatService.getChats(mailbox, user).map(seq => Ok(Json.toJson(seq)))
      } else Future.successful(NotFound)
    }
  }
}

