package controllers

import java.io.File
import java.nio.file.{ Files, Path, Paths }

import javax.inject._
import model.dtos._
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

import scala.concurrent.{ ExecutionContext, Future }
import model.types.Mailbox

@Singleton
class ChatController @Inject() (implicit val ec: ExecutionContext, config: Configuration, cc: ControllerComponents,
  chatService: ChatService, authenticatedUserAction: AuthenticatedUserAction)
  extends AbstractController(cc) {

  def getChat(id: String): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>

      chatService.getChat(id, authenticatedRequest.userId).map {
        case Some(chatDTO) => Ok(Json.toJson(chatDTO))
        case None => NotFound(chatNotFound)
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

  def postAttachment(chatId: String, emailId: String): Action[MultipartFormData[File]] =
    authenticatedUserAction.async(parse.multipartFormData(handleFilePartAsFile)) {
      implicit authenticatedRequest =>
        authenticatedRequest.body.file("attachment").map {
          case FilePart(key, filename, contentType, file) => uploadAttachment(file)
        } match {
          case None => Future.successful(BadRequest(missingAttachment))
          case Some(attachmentPath) =>
            chatService.postAttachment(chatId, emailId, authenticatedRequest.userId, attachmentPath).map {
              case Some(attachmentId) => Ok(Json.toJson(attachmentId))
              case None => NotFound
            }
        }
    }

/********** Auxiliary Methods *************/

  type FilePartHandler[A] = FileInfo => Accumulator[ByteString, FilePart[A]]

  /**
   * Uses a custom FilePartHandler to return a type of "File" rather than using Play's TemporaryFile class.
   * Deletion must happen explicitly on completion, rather than TemporaryFile
   * (which uses finalization to delete temporary files).
   */
  private def handleFilePartAsFile: FilePartHandler[File] = {
    case FileInfo(partName, filename, contentType) =>
      val path: Path = Files.createTempFile("multipartBody", filename)
      val fileSink: Sink[ByteString, Future[IOResult]] = FileIO.toPath(path)
      val accumulator: Accumulator[ByteString, IOResult] = Accumulator(fileSink)
      accumulator.map {
        case IOResult(count, status) => println(s"count = $count, status = $status"); FilePart(partName, filename, contentType, path.toFile)
      }
  }

  private def uploadAttachment(file: File): String = {
    val filePath = file.toPath
    val uploadPath = config.get[String]("uploadDirectory") + "\\" + filePath.getFileName

    Files.move(filePath, Paths.get(uploadPath)) //Move file

    println(s"path = $uploadPath")

    Files.deleteIfExists(filePath) //Delete temporary file

    uploadPath
  }

}