package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json.Json
import services.ChatService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import model.types.Mailbox
import validations.CategoryNames

@Singleton
class ChatController @Inject() (cc: ControllerComponents, chatService: ChatService)
  extends AbstractController(cc) {

  def getChat(id: String): Action[AnyContent] =
    Action.async {
      val userId = "148a3b1b-8326-466d-8c27-1bd09b8378f3"

      chatService.getChat(id, userId).map {
        case Some(chat) => Ok(Json.toJson(chat))
        case None => NotFound
      }
    }

  def getChats(mailboxString: String): Action[AnyContent] = {
    Action.async {
      if (CategoryNames.validMailboxes.contains(mailboxString)) {
        val mailbox = Mailbox(mailboxString)
        val user = "148a3b1b-8326-466d-8c27-1bd09b8378f3"
        chatService.getChats(mailbox, user).map(seq => Ok(Json.toJson(seq)))
      } else Future.successful(NotFound)
    }
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

  def postAddress(): Action[JsValue] =
  Action.async(parse.json) { implicit request: Request[JsValue] =>
  val jsonValue = request.body
  jsonValue.validate[AddressDTO].fold(
  errors => Future.successful(BadRequest(JsError.toJson(errors))),
  addressDTO => addressService.postAddress(addressDTO.address).map(result =>
  Ok(Json.toJson(result))))
}

  def deleteAddress(id: String): Action[AnyContent] =
  Action.async {
  addressService.deleteAddress(id).map {
  case true => NoContent
  case _ => NotFound
}
}*/
//endregion

