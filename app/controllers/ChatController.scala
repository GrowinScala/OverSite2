package controllers

import javax.inject._
import model.dtos._
import play.api.mvc._
import play.api.libs.json._
import services.ChatService
import utils.Jsons._

import scala.concurrent.{ ExecutionContext, Future }
import model.types.{ Mailbox, Page, PerPage }

@Singleton
class ChatController @Inject() (implicit val ec: ExecutionContext, cc: ControllerComponents, chatService: ChatService,
  authenticatedUserAction: AuthenticatedUserAction)
  extends AbstractController(cc) {

  def getChat(id: String): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>

      chatService.getChat(id, authenticatedRequest.userId).map {
        case Some(chatDTO) => Ok(Json.toJson(chatDTO))
        case None => NotFound(chatNotFound)
      }
  }

  def getChats(mailbox: Mailbox, page: Page, perPage: PerPage): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>

      chatService.getChats(mailbox, page, perPage, authenticatedRequest.userId)
        .map {
          case Some((chatsPreviewDTO, totalCount, lastPage)) =>
            val chats = Json.obj("chats" -> Json.toJson(chatsPreviewDTO))

            val metadata = Json.obj("_metadata" -> Json.toJsObject(PaginationDTO(
              totalCount,
              LinksDTO(
                self = makeGetChatsLink(mailbox, page, perPage, authenticatedRequest),
                first = makeGetChatsLink(mailbox, Page(0), perPage, authenticatedRequest),
                previous = if (page == 0) None
                else Some(makeGetChatsLink(mailbox, page - 1, perPage, authenticatedRequest)),
                next = if (page >= lastPage) None
                else Some(makeGetChatsLink(mailbox, page + 1, perPage, authenticatedRequest)),
                last = makeGetChatsLink(mailbox, lastPage, perPage, authenticatedRequest)))))
            Ok(chats ++ metadata)
          case None => InternalServerError(internalError)
        }
  }

  def postChat: Action[JsValue] = {
    authenticatedUserAction.async(parse.json) { authenticatedRequest =>
      val jsonValue = authenticatedRequest.request.body

      jsonValue.validate[CreateChatDTO].fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        createChatDTO => chatService.postChat(createChatDTO, authenticatedRequest.userId)
          .map {
            case Some(crChatDTO) => Ok(Json.toJson(crChatDTO))
            case None => InternalServerError(internalError)
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
        case None => NotFound(emailNotFound)
      }
  }

  def deleteChat(chatId: String): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>
      chatService.deleteChat(chatId, authenticatedRequest.userId).map(if (_) NoContent else NotFound(chatNotFound))
  }

  def deleteDraft(chatId: String, emailId: String): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>
      chatService.deleteDraft(chatId, emailId, authenticatedRequest.userId).map(if (_) NoContent
      else NotFound(emailNotFound))
  }

  def deleteOverseer(chatId: String, oversightId: String): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>
      chatService.deleteOverseer(chatId, oversightId, authenticatedRequest.userId)
        .map(if (_) NoContent else NotFound(overseerNotFound))
  }

  def postOverseers(chatId: String): Action[JsValue] = {
    authenticatedUserAction.async(parse.json) { authenticatedRequest =>
      val jsonValue = authenticatedRequest.request.body

      jsonValue.validate[Set[PostOverseerDTO]].fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        postOverseersDTO => chatService.postOverseers(postOverseersDTO, chatId, authenticatedRequest.userId)
          .map {
            case Some(result) => Ok(Json.toJson(result))
            case None => NotFound(chatNotFound)
          })
    }
  }

  /**
   * Gets the user's overseers for the given chat
   * @param chatId The chat's Id
   * @return A postOverseersDTO that contains the address and oversightId for each overseear or 404 NotFound
   */
  def getOverseers(chatId: String): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>

      chatService.getOverseers(chatId, authenticatedRequest.userId).map {
        case Some(postOverseersDTO) => Ok(Json.toJson(postOverseersDTO))
        case None => NotFound(chatNotFound)
      }
  }

  def getOversights: Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>
      chatService.getOversights(authenticatedRequest.userId)
        .map(oversightDTO => Ok(Json.toJson(oversightDTO)))
  }

  //region Auxiliary Methods
  def makeGetChatsLink(mailbox: Mailbox, page: Page, perPage: PerPage, auth: AuthenticatedUser[AnyContent]): String =
    routes.ChatController.getChats(mailbox, page, perPage).absoluteURL(auth.secure)(auth.request)
  //endregion

}