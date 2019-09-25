package controllers

import javax.inject._
import model.dtos.{ CreateChatDTO, PatchChatDTO, UpsertEmailDTO }
import play.api.mvc._
import play.api.libs.json.{ JsError, JsValue, Json }
import services.ChatService
import utils.Jsons._

import scala.concurrent.{ ExecutionContext, Future }
import model.types.Mailbox

@Singleton
class ChatController @Inject() (implicit val ec: ExecutionContext, cc: ControllerComponents, chatService: ChatService,
  authenticatedUserAction: AuthenticatedUserAction)
  extends AbstractController(cc) {

  def getChat(id: String): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>

      chatService.getChat(id, authenticatedRequest.userId).map {
        case Some(chatDTO) => Ok(Json.toJson(chatDTO))
        case None => NotFound
      }
  }

  def getChats(mailbox: Mailbox): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>
      chatService.getChats(mailbox, authenticatedRequest.userId).map(seq => Ok(Json.toJson(seq)))
  }

  def postChat: Action[JsValue] = {
    authenticatedUserAction.async(parse.json) { authenticatedRequest =>
      val jsonValue = authenticatedRequest.request.body

      jsonValue.validate[CreateChatDTO].fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        createChatDTO => chatService.postChat(createChatDTO, authenticatedRequest.userId)
          .map {
            case Some(crChatDTO) => Ok(Json.toJson(crChatDTO))
            case None => InternalServerError
          })
    }
  }

  // Note that this method will return NotFound if the chatId exists but the user does not have access to it
  def postEmail(chatId: String): Action[JsValue] = {
    authenticatedUserAction.async(parse.json) { authenticatedRequest =>
      val jsonValue = authenticatedRequest.request.body

      jsonValue.validate[UpsertEmailDTO].fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        upsertEmailDTO => chatService.postEmail(upsertEmailDTO, chatId, authenticatedRequest.userId)
          .map {
            case Some(result) => Ok(Json.toJson(result))
            case None => NotFound(chatNotFound)
          })
    }
  }

  def patchEmail(chatId: String, emailId: String): Action[JsValue] = {
    authenticatedUserAction.async(parse.json) { authenticatedRequest =>
      val jsonValue = authenticatedRequest.request.body

      jsonValue.validate[UpsertEmailDTO].fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        upsertEmailDTO => chatService.patchEmail(upsertEmailDTO, chatId, emailId, authenticatedRequest.userId)
          .map {
            case Some(result) => Ok(Json.toJson(result))
            case None => NotFound(emailNotFound)
          })
    }
  }

  def patchChat(chatId: String): Action[JsValue] = {
    authenticatedUserAction.async(parse.json) { authenticatedRequest =>
      val jsonValue = authenticatedRequest.request.body

      jsonValue.validate[PatchChatDTO].fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        patchChatDTO => chatService.patchChat(patchChatDTO, chatId, authenticatedRequest.userId).map {
          case Some(result) => Ok(Json.toJson(result))
          case None => NotFound(chatNotFound)
        })
    }
  }

  def getEmail(chatId: String, emailId: String): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>

      chatService.getEmail(chatId, emailId, authenticatedRequest.userId).map {
        case Some(chatDTO) => Ok(Json.toJson(chatDTO))
        case None => NotFound
      }
  }

  def deleteChat(chatId: String): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>
      chatService.deleteChat(chatId, authenticatedRequest.userId).map(if (_) NoContent else NotFound)
  }

  def deleteDraft(chatId: String, emailId: String): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>
      chatService.deleteDraft(chatId, emailId, authenticatedRequest.userId).map(if (_) NoContent else NotFound)
  }

}