package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json.Json
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
        case Some(chat) => Ok(Json.toJson(chat))
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

