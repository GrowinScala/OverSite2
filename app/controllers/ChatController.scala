package controllers

import java.io.File
import java.nio.file.{ Files, Path, Paths }

import javax.inject._
import model.dtos._
import play.api.mvc._
import play.api.libs.json._
import services.ChatService
import utils.Jsons._
import akka.stream.IOResult
import akka.stream.scaladsl._
import akka.util.ByteString
import play.api._
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.streams._
import play.api.libs.json.{ JsError, JsValue, Json }
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import play.core.parsers.Multipart.FileInfo
import repositories.RepUtils.RepConstants._

import scala.concurrent.{ ExecutionContext, Future }
import model.types.{ Mailbox, Page, PerPage }

@Singleton
class ChatController @Inject() (implicit val ec: ExecutionContext, cc: ControllerComponents,
  chatService: ChatService, authenticatedUserAction: AuthenticatedUserAction)
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
              PageLinksDTO(
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

  /**
   * Gets the user's overseers for the given chat
   * @param chatId The chat's Id
   * @return A postOverseersDTO that contains the address and oversightId for each overseear or 404 NotFound
   */
  def getOverseers(chatId: String, page: Page, perPage: PerPage): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>

      chatService.getOverseers(chatId, page, perPage, authenticatedRequest.userId).map {
        case Right((postOverseerDTO, totalCount, lastPage)) =>
          val chats = Json.obj("overseers" -> Json.toJson(postOverseerDTO))

          val metadata = Json.obj("_metadata" -> Json.toJsObject(PaginationDTO(
            totalCount,
            PageLinksDTO(
              self = makeGetOverseersLink(chatId, page, perPage, authenticatedRequest),
              first = makeGetOverseersLink(chatId, Page(0), perPage, authenticatedRequest),
              previous = if (page == 0) None
              else Some(makeGetOverseersLink(chatId, page - 1, perPage, authenticatedRequest)),
              next = if (page >= lastPage) None
              else Some(makeGetOverseersLink(chatId, page + 1, perPage, authenticatedRequest)),
              last = makeGetOverseersLink(chatId, lastPage, perPage, authenticatedRequest)))))
          Ok(chats ++ metadata)
        case Left(`chatNotFound`) => BadRequest(chatNotFound)
        case Left(_) => InternalServerError(internalError)
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

  def getOversights: Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>
      chatService.getOversights(authenticatedRequest.userId)
        .map {
          case Some(oversightDTO) =>
            val oversightsPreview = Json.obj("oversightsPreview" -> Json.toJson(oversightDTO))
            val metadata = Json.obj("_metadata" ->
              Json.obj("links" ->
                Json.obj(
                  "overseeing" -> routes.ChatController.getOverseeings(Page(DEFAULT_PAGE), PerPage(DEFAULT_PER_PAGE))
                    .absoluteURL(authenticatedRequest.secure)(authenticatedRequest.request),
                  "overseen" -> routes.ChatController.getOverseens(Page(DEFAULT_PAGE), PerPage(DEFAULT_PER_PAGE))
                    .absoluteURL(authenticatedRequest.secure)(authenticatedRequest.request))))
            Ok(oversightsPreview ++ metadata)
          case None => NotFound(oversightsNotFound)
        }
  }

  def getOverseeings(page: Page, perPage: PerPage): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>
      chatService.getOverseeings(page, perPage, authenticatedRequest.userId).map {
        case Some((seqChatOverseeingDTO, totalCount, lastPage)) =>
          val overseeings = Json.obj("overseeings" -> Json.toJson(seqChatOverseeingDTO))

          val metadata = Json.obj("_metadata" -> Json.toJsObject(PaginationDTO(
            totalCount,
            PageLinksDTO(
              self = makeGetOverseeingsLink(page, perPage, authenticatedRequest),
              first = makeGetOverseeingsLink(Page(0), perPage, authenticatedRequest),
              previous = if (page == 0) None
              else Some(makeGetOverseeingsLink(page - 1, perPage, authenticatedRequest)),
              next = if (page >= lastPage) None
              else Some(makeGetOverseeingsLink(page + 1, perPage, authenticatedRequest)),
              last = makeGetOverseeingsLink(lastPage, perPage, authenticatedRequest)))))
          Ok(overseeings ++ metadata)
        case None => InternalServerError(internalError)
      }
  }

  def getOverseens(page: Page, perPage: PerPage): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>
      chatService.getOverseens(page, perPage, authenticatedRequest.userId).map {
        case Some((seqChatOverseenDTO, totalCount, lastPage)) =>
          val overseens = Json.obj("overseens" -> Json.toJson(seqChatOverseenDTO))

          val metadata = Json.obj("_metadata" -> Json.toJsObject(PaginationDTO(
            totalCount,
            PageLinksDTO(
              self = makeGetOverseensLink(page, perPage, authenticatedRequest),
              first = makeGetOverseensLink(Page(0), perPage, authenticatedRequest),
              previous = if (page == 0) None
              else Some(makeGetOverseensLink(page - 1, perPage, authenticatedRequest)),
              next = if (page >= lastPage) None
              else Some(makeGetOverseensLink(page + 1, perPage, authenticatedRequest)),
              last = makeGetOverseensLink(lastPage, perPage, authenticatedRequest)))))
          Ok(overseens ++ metadata)
        case None => InternalServerError(internalError)
      }
  }

  def postAttachment(chatId: String, emailId: String): Action[MultipartFormData[File]] =
    authenticatedUserAction.async(parse.multipartFormData(handleFilePartAsFile)) {
      implicit authenticatedRequest =>
        authenticatedRequest.body.file("attachment") match {
          case Some(FilePart(key, filename, contentType, file)) =>
            chatService.postAttachment(chatId, emailId, authenticatedRequest.userId, filename, file).map {
              case Some(attachmentId) => Ok(Json.obj("attachmentId" -> attachmentId))
              case None => NotFound(chatNotFound)
            }
          case None => Future.successful(BadRequest(missingAttachment))
        }
    }

  //region Auxiliary Methods
  def makeGetChatsLink(mailbox: Mailbox, page: Page, perPage: PerPage, auth: AuthenticatedUser[AnyContent]): String =
    routes.ChatController.getChats(mailbox, page, perPage).absoluteURL(auth.secure)(auth.request)

  def makeGetOverseersLink(chatId: String, page: Page, perPage: PerPage, auth: AuthenticatedUser[AnyContent]): String =
    routes.ChatController.getOverseers(chatId, page, perPage).absoluteURL(auth.secure)(auth.request)

  def makeGetOverseeingsLink(page: Page, perPage: PerPage, auth: AuthenticatedUser[AnyContent]): String =
    routes.ChatController.getOverseeings(page, perPage).absoluteURL(auth.secure)(auth.request)

  def makeGetOverseensLink(page: Page, perPage: PerPage, auth: AuthenticatedUser[AnyContent]): String =
    routes.ChatController.getOverseens(page, perPage).absoluteURL(auth.secure)(auth.request)

  type FilePartHandler[A] = FileInfo => Accumulator[ByteString, FilePart[A]]

  /**
   * Uses a custom FilePartHandler to return a type of "File" rather than using Play's TemporaryFile class.
   * Deletion must happen explicitly on completion, rather than TemporaryFile
   * (which uses finalization to delete temporary files).
   */
  private def handleFilePartAsFile: FilePartHandler[File] = {
    case FileInfo(partName, filename, contentType) =>
      val temporaryPath: Path = Files.createTempFile("", "")

      val fileSink: Sink[ByteString, Future[IOResult]] = FileIO.toPath(temporaryPath)
      val accumulator: Accumulator[ByteString, IOResult] = Accumulator(fileSink)
      accumulator.map {
        case IOResult(count, status) => FilePart(partName, filename, contentType, temporaryPath.toFile)
      }
  }
  //endregion

}