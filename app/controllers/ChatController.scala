package controllers

import javax.inject._
import model.dtos.CreateChatDTO
import play.api.mvc._
import play.api.libs.json.{ JsError, JsValue, Json }
import services.{ AuthenticationService, ChatService }

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

}

//region Old
/*def getAddress(id: String): Action[AnyContent] =
Action.async {
  addressService.getAddress(id).map {
  case Some(addressDTO: AddressDTO) => Ok(Json.toJson(addressDTO))
  case None => NotFound
    }
  }

  def postChat: Action[JsValue] = {
    Action.async(parse.json) { implicit request: Request[JsValue] =>
      val jsonValue = request.body

      jsonValue.validate[CreateChatDTO].fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        createChatDTO => chatService.postChat(createChatDTO, userId)
          .map(result => Ok(Json.toJson(result))))
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

