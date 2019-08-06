package controllers

import javax.inject._
import model.dtos.{ CreateChatDTO, CreateEmailDTO }
import play.api.mvc._
import play.api.libs.json.{ JsError, JsValue, Json }
import services.ChatService
import utils.Jsons._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import model.types.Mailbox
import validations.CategoryNames

@Singleton
class ChatController @Inject() (cc: ControllerComponents, chatService: ChatService,
  authenticatedUserAction: AuthenticatedUserAction)
  extends AbstractController(cc) {

  def getChat(id: String): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>

      chatService.getChat(id, authenticatedRequest.userId).map {
        case Some(chatDTO) => Ok(Json.toJson(chatDTO))
        case None => NotFound
      }
  }

  def getChats(mailboxString: String): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>
      if (CategoryNames.validMailboxes.contains(mailboxString)) {
        val mailbox = Mailbox(mailboxString)
        chatService.getChats(mailbox, authenticatedRequest.userId).map(seq => Ok(Json.toJson(seq)))
      } else Future.successful(NotFound)

  }

  def postChat: Action[JsValue] = {
    authenticatedUserAction.async(parse.json) { authenticatedRequest =>
      val jsonValue = authenticatedRequest.request.body

      jsonValue.validate[CreateChatDTO].fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        createChatDTO => chatService.postChat(createChatDTO, authenticatedRequest.userId)
          .map(result => Ok(Json.toJson(result))))
    }
  }

  // Note that this method will return NotFound if the chatId exists but the user does not have access to it
  def postEmail(chatId: String): Action[JsValue] = {
    authenticatedUserAction.async(parse.json) { authenticatedRequest =>
      val jsonValue = authenticatedRequest.request.body

      jsonValue.validate[CreateEmailDTO].fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        createEmailDTO => chatService.postEmail(createEmailDTO, chatId, authenticatedRequest.userId)
          .map {
            case Some(result) => Ok(Json.toJson(result))
            case None => NotFound(chatNotFound)
          })
    }
  }

}

//region Old
/*
	def deleteAddress(id: String): Action[AnyContent] =
  Action.async {
  addressService.deleteAddress(id).map {
  case true => NoContent
  case _ => NotFound
}
}*/
//endregion

