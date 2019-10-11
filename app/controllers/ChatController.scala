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
          .map(result => Ok(Json.toJson(result))))
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

  def deleteOverseer(chatId: String, oversightId: String): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>
      chatService.deleteOverseer(chatId, oversightId, authenticatedRequest.userId)
        .map(if (_) NoContent else NotFound)
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

  def getOverseers(chatId: String): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>

      chatService.getOverseers(chatId, authenticatedRequest.userId).map {
        case Some(postOverseersDTO) => Ok(Json.toJson(postOverseersDTO))
        case None => NotFound
      }
  }

/************************************************************************************************/

  def postAttachment(chatId: String, emailId: String): Action[MultipartFormData[File]] =
    Action(parse.multipartFormData(handleFilePartAsFile)) { implicit request =>
      val fileOption = request.body.file("name").map {
        case FilePart(key, filename, contentType, file) => operateOnTempFile(file)
      }

      Ok(s"file size = ${fileOption.getOrElse("no file")}")
    }

  type FilePartHandler[A] = FileInfo => Accumulator[ByteString, FilePart[A]]

  /**
   * Uses a custom FilePartHandler to return a type of "File" rather than
   * using Play's TemporaryFile class.  Deletion must happen explicitly on
   * completion, rather than TemporaryFile (which uses finalization to
   * delete temporary files).
   *
   * @return
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

  /**
   * A generic operation on the temporary file that deletes the temp file after completion.
   */
  private def operateOnTempFile(file: File): Long = {
    val size = Files.size(file.toPath)
    val filename = file.toPath.getFileName
    val myUploadDirectory = config.get[String]("myUploadDir")

    val moveFile = Files.move(file.toPath, Paths.get(myUploadDirectory + s"\\$filename"))
    println(s"path = $myUploadDirectory")
    val deleteTemporaryFile = Files.deleteIfExists(file.toPath)

    size
  }

  /**
   * Uploads a multipart file as a POST request.
   *
   * @return
   */
  def upload: Action[MultipartFormData[File]] =
    authenticatedUserAction.async(parse.multipartFormData(handleFilePartAsFile)) { implicit authenticatedRequest =>
      val fileOption = authenticatedRequest.body.file("attachment").map {
        case FilePart(key, filename, contentType, file) =>
          println(s"key = $key, filename = $filename, contentType = ${contentType.getOrElse("")}")
          operateOnTempFile(file)
      }

      Future.successful(Ok(s"file size = ${fileOption.getOrElse("no file")}"))
    }

}