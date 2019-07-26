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

  //val userId = "148a3b1b-8326-466d-8c27-1bd09b8378f3" //user 1 Beatriz
  val userId = "adcd6348-658a-4866-93c5-7e6d32271d8d" //user 2 João
  //val userId = "25689204-5a8e-453d-bfbc-4180ff0f97b9" //user 3 Valter
  //val userId = "ef63108c-8128-4294-8346-bd9b5143ff22" //user 4 Pedro L
  //val userId = "e598ee8e-b459-499f-94d1-d4f66d583264" //user 5 Pedro C
  //val userId = "261c9094-6261-4704-bfd0-02821c235eff" //user 6 Rui

  //val userId = "12345678-1234-5678-9012-123456789100" //Non existing user

  def getChat(id: String): Action[AnyContent] =
    Action.async {
      chatService.getChat(id, userId).map {
        case Some(chatDTO) => Ok(Json.toJson(chatDTO))
        case None => NotFound
      }
    }

  def getChats(mailboxString: String): Action[AnyContent] = {
    Action.async {
      if (CategoryNames.validMailboxes.contains(mailboxString)) {
        val mailbox = Mailbox(mailboxString)
        //val user = "148a3b1b-8326-466d-8c27-1bd09b8378f3"
        chatService.getChats(mailbox, userId).map(seq => Ok(Json.toJson(seq)))
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

