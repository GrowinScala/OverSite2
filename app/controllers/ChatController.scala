package controllers

import javax.inject._
import model.dtos._
import services.ChatService
import utils.Jsons._
import akka.stream.scaladsl._
import play.api.libs.json.{ JsError, JsValue, Json }
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import model.types._
import model.types.Sort._
import org.slf4j.MDC
import play.api.Logger
import play.api.libs.Files.TemporaryFile
import repositories.RepUtils.RepConstants._
import repositories.RepUtils.types.OrderBy.DefaultOrder
import utils.LogMessages._
import utils.Headers._

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class ChatController @Inject() (implicit val ec: ExecutionContext, cc: ControllerComponents,
  chatService: ChatService, authenticatedUserAction: AuthenticatedUserAction)
  extends AbstractController(cc) {

  import ChatController._

  private val log = Logger(this.getClass)

  def getChat(chatId: String, page: Page, perPage: PerPage,
    sort: Sort): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>
      val userId = authenticatedRequest.userId
      MDC.put("controllerMethod", "getChat")
      log.info(logRequest(logGetChat))
      log.debug(logRequest(s"$logGetChat: userId=$userId, chatId=$chatId, page=${page.value}," +
        s" perPage=${perPage.value}, sort=$sort"))
      if (sort.sortBy == SORT_BY_DATE || sort.sortBy == DEFAULT_SORT)
        chatService.getChat(chatId, page, perPage, sort, authenticatedRequest).map {
          case Right((chatDTO, totalCount, lastPage)) =>
            val chat = Json.obj("chat" -> Json.toJson(chatDTO))

            val metadata = Json.obj("_metadata" -> Json.toJsObject(PaginationDTO(
              totalCount,
              PageLinksDTO(
                self = makeGetChatLink(chatId, page, perPage, sort, authenticatedRequest),
                first = makeGetChatLink(chatId, Page(0), perPage, sort, authenticatedRequest),
                previous = if (page == 0) None
                else Some(makeGetChatLink(chatId, page - 1, perPage, sort, authenticatedRequest)),
                next = if (page >= lastPage) None
                else Some(makeGetChatLink(chatId, page + 1, perPage, sort, authenticatedRequest)),
                last = makeGetChatLink(chatId, lastPage, perPage, sort, authenticatedRequest)))))
            log.info("The chat was retrived")
            log.debug(s"The chat was retrived: ${(chat ++ metadata).toString}")
            Ok(chat ++ metadata)
          case Left(`chatNotFound`) =>
            log.info(serviceReturn(chatNotFound))
            log.debug(s"${serviceReturn(chatNotFound)}: chatId=$chatId, userId=$userId")
            NotFound(chatNotFound)
          case Left(_) =>
            log.error(serviceReturn(internalError))
            InternalServerError(internalError)
        }
      else {
        log.info(badRequest(invalidSortBy))
        log.debug(s"${badRequest(invalidSortBy)}: sort: $sort")
        Future.successful(BadRequest(invalidSortBy))
      }
  }

  def getChats(mailbox: Mailbox, page: Page, perPage: PerPage,
    sort: Sort): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>
      MDC.put("controllerMethod", "getChats")
      log.info(logRequest(logGetChats))
      log.debug(logRequest(s"$logGetChats: userId=${authenticatedRequest.userId}, mailbox=$mailbox," +
        s" page=${page.value}, perPage=${perPage.value}, sort=$sort"))
      if (sort.sortBy == SORT_BY_DATE || sort.sortBy == DEFAULT_SORT)
        chatService.getChats(mailbox, page, perPage, sort, authenticatedRequest)
          .map {
            case Some((chatsPreviewDTO, totalCount, lastPage)) =>
              val chats = Json.obj("chats" -> Json.toJson(chatsPreviewDTO))

              val metadata = Json.obj("_metadata" -> Json.toJsObject(PaginationDTO(
                totalCount,
                PageLinksDTO(
                  self = makeGetChatsLink(mailbox, page, perPage, sort, authenticatedRequest),
                  first = makeGetChatsLink(mailbox, Page(0), perPage, sort, authenticatedRequest),
                  previous = if (page == 0) None
                  else Some(makeGetChatsLink(mailbox, page - 1, perPage, sort, authenticatedRequest)),
                  next = if (page >= lastPage) None
                  else Some(makeGetChatsLink(mailbox, page + 1, perPage, sort, authenticatedRequest)),
                  last = makeGetChatsLink(mailbox, lastPage, perPage, sort, authenticatedRequest)))))

              log.info("The chats were retrived")
              log.debug(s"The chats were retrived: ${(chats ++ metadata).toString}")

              Ok(chats ++ metadata)
            case None =>
              log.error(serviceReturn(logNonePagError))
              log.debug(serviceReturn(s"$logNonePagError: page=$page, perPage=$perPage"))
              log.error(serviceReturn(internalError))

              InternalServerError(internalError)
          }
      else {
        log.info(badRequest(invalidSortBy))
        log.debug(s"${badRequest(invalidSortBy)}: sort: $sort")
        Future.successful(BadRequest(invalidSortBy))
      }
  }

  /**
   * Gets the user's overseers for the given chat
   *
   * @param chatId The chat's Id
   * @return A postOverseersDTO that contains the address and oversightId for each overseear or 404 NotFound
   */
  def getOverseers(chatId: String, page: Page, perPage: PerPage,
    sort: Sort): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>
      val userId = authenticatedRequest.userId
      MDC.put("controllerMethod", "getOverseers")
      log.info(logRequest(logGetOverseers))
      log.debug(logRequest(s"$logGetOverseers: userId=$userId," +
        s" chatId=$chatId, page=${page.value}, perPage=${perPage.value}, sort=$sort"))
      if (sort.sortBy == SORT_BY_ADDRESS || sort.sortBy == DEFAULT_SORT)
        chatService.getOverseers(chatId, page, perPage, sort, userId).map {
          case Right((seqPostOverseerDTO, totalCount, lastPage)) =>
            val overseers = Json.obj("overseers" -> Json.toJson(seqPostOverseerDTO))

            val metadata = Json.obj("_metadata" -> Json.toJsObject(PaginationDTO(
              totalCount,
              PageLinksDTO(
                self = makeGetOverseersLink(chatId, page, perPage, sort, authenticatedRequest),
                first = makeGetOverseersLink(chatId, Page(0), perPage, sort, authenticatedRequest),
                previous = if (page == 0) None
                else Some(makeGetOverseersLink(chatId, page - 1, perPage, sort, authenticatedRequest)),
                next = if (page >= lastPage) None
                else Some(makeGetOverseersLink(chatId, page + 1, perPage, sort, authenticatedRequest)),
                last = makeGetOverseersLink(chatId, lastPage, perPage, sort, authenticatedRequest)))))
            log.info("The overseers were retrived")
            log.debug(s"The overseers were retrived: ${(overseers ++ metadata).toString}")
            Ok(overseers ++ metadata)
          case Left(`chatNotFound`) =>
            log.info(serviceReturn(chatNotFound))
            log.debug(s"${serviceReturn(chatNotFound)}: chatId=$chatId, userId=$userId")
            NotFound(chatNotFound)
          case Left(_) =>
            log.error(serviceReturn(logNonePagError))
            log.debug(serviceReturn(s"$logNonePagError: page=$page, perPage=$perPage"))
            log.error(serviceReturn(internalError))
            InternalServerError(internalError)
        }
      else {
        log.info(badRequest(invalidSortBy))
        Future.successful(BadRequest(invalidSortBy))
      }
  }

  def postChat: Action[JsValue] = {
    authenticatedUserAction.async(parse.json) { authenticatedRequest =>
      val jsonValue = authenticatedRequest.request.body
      val userId = authenticatedRequest.userId
      MDC.put("controllerMethod", "postChat")
      log.info(logRequest(logPostChat))
      log.debug(logRequest(s"$logPostChat: userId=$userId, requestBody=$jsonValue"))

      jsonValue.validate[CreateChatDTO].fold(
        errors => {
          log.info(invalidJson(CreateChatDTO, errors))
          log.debug(invalidJson(CreateChatDTO))
          Future.successful(BadRequest(JsError.toJson(errors)))
        },
        createChatDTO => chatService.postChat(createChatDTO, userId)
          .map {
            case Some(crChatDTO) =>
              log.info("The chat was posted")
              log.debug(s"The chat was posted: userId=$userId, DTO=$crChatDTO")
              Ok(Json.toJson(crChatDTO))
            case None =>
              log.error(serviceReturn(logNoneAuthError))
              log.debug(serviceReturn(s"$logNoneAuthError: userId=$userId, token=${
                authenticatedRequest.headers
                  .get(auth)
              }"))
              InternalServerError(internalError)
          })
    }
  }

  // Note that this method will return NotFound if the chatId exists but the user does not have access to it
  def postEmail(chatId: String): Action[JsValue] = {
    authenticatedUserAction.async(parse.json) { authenticatedRequest =>
      val jsonValue = authenticatedRequest.request.body
      val userId = authenticatedRequest.userId
      MDC.put("controllerMethod", "postEmail")
      log.info(logRequest(logPostEmail))
      log.debug(logRequest(s"$logPostEmail: userId=$userId, chatId=$chatId, requestBody=$jsonValue"))

      jsonValue.validate[UpsertEmailDTO].fold(
        errors => {
          log.info(invalidJson(UpsertEmailDTO, errors))
          log.debug(invalidJson(UpsertEmailDTO))
          Future.successful(BadRequest(JsError.toJson(errors)))
        },
        upsertEmailDTO => chatService.postEmail(upsertEmailDTO, chatId, userId)
          .map {
            case Some(result) =>
              log.info("The email was posted")
              log.debug(s"The email was posted: userId=$userId, chatId=$chatId, DTO=$result")
              Ok(Json.toJson(result))
            case None =>
              log.info(s"$chatNotFound")
              log.debug(s"$chatNotFound. chatId=$chatId, userId=$userId")
              NotFound(chatNotFound)
          })
    }
  }

  def patchEmail(chatId: String, emailId: String): Action[JsValue] = {
    authenticatedUserAction.async(parse.json) { authenticatedRequest =>
      val jsonValue = authenticatedRequest.request.body
      val userId = authenticatedRequest.userId
      MDC.put("controllerMethod", "patchEmail")
      log.info(logRequest(logPatchEmail))
      log.debug(logRequest(s"$logPatchEmail: userId=$userId, chatId=$chatId, emailId=$emailId, requestBody=$jsonValue"))

      jsonValue.validate[UpsertEmailDTO].fold(
        errors => {
          log.info(invalidJson(UpsertEmailDTO, errors))
          log.debug(invalidJson(UpsertEmailDTO))
          Future.successful(BadRequest(JsError.toJson(errors)))
        },
        upsertEmailDTO => chatService.patchEmail(upsertEmailDTO, chatId, emailId, authenticatedRequest)
          .map {
            case Some(result) =>
              log.info("The email was patched")
              log.debug(s"The email was patched: userId=$userId, chatId=$chatId, emailId=$emailId, DTO=$result")
              Ok(Json.toJson(result))
            case None =>
              log.info(s"$emailNotFound")
              log.debug(s"$emailNotFound. chatId=$chatId, emailId=$emailId, userId=$userId")
              NotFound(emailNotFound)
          })
    }
  }

  def patchChat(chatId: String): Action[JsValue] = {
    authenticatedUserAction.async(parse.json) { authenticatedRequest =>
      val jsonValue = authenticatedRequest.request.body
      val userId = authenticatedRequest.userId
      MDC.put("controllerMethod", "patchChat")
      log.info(logRequest(logPatchChat))
      log.debug(logRequest(s"$logPatchChat: userId=$userId, chatId=$chatId, requestBody=$jsonValue"))

      jsonValue.validate[PatchChatDTO].fold(
        errors => {
          log.info(invalidJson(PatchChatDTO, errors))
          log.debug(invalidJson(PatchChatDTO))
          Future.successful(BadRequest(JsError.toJson(errors)))
        },
        patchChatDTO => chatService.patchChat(patchChatDTO, chatId, userId).map {
          case Some(result) =>
            log.info("The chat was patched")
            log.debug(s"The chat was patched: userId=$userId, chatId=$chatId, DTO=$result")
            Ok(Json.toJson(result))
          case None =>
            log.info(s"$chatNotFound")
            log.debug(s"$chatNotFound. chatId=$chatId, userId=$userId")
            NotFound(chatNotFound)
        })
    }
  }

  def getEmail(chatId: String, emailId: String): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>
      val userId = authenticatedRequest.userId
      MDC.put("controllerMethod", "getEmail")
      log.info(logRequest(logGetEmail))
      log.debug(logRequest(s"$logGetEmail: userId=$userId, chatId=$chatId, emailId=$emailId"))

      chatService.getEmail(chatId, emailId, authenticatedRequest).map {
        case Some(chatDTO) =>
          log.info("The email was retrived")
          log.debug(s"The email was retrived: $chatDTO")
          Ok(Json.toJson(chatDTO))
        case None =>
          log.info(s"$emailNotFound")
          log.debug(s"$emailNotFound. chatId=$chatId, emailId=$emailId, userId=$userId")
          NotFound(emailNotFound)
      }
  }

  def deleteChat(chatId: String): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>
      val userId = authenticatedRequest.userId
      MDC.put("controllerMethod", "deleteChat")
      log.info(logRequest(logDeleteChat))
      log.debug(logRequest(s"$logDeleteChat: userId=$userId, chatId=$chatId"))

      chatService.deleteChat(chatId, userId).map(
        if (_) {
          log.info("The chat was deleted")
          log.debug(s"The chat was deleted: chatId=$chatId, userId=$userId")
          NoContent
        } else {
          log.info(s"$chatNotFound")
          log.debug(s"$chatNotFound. chatId=$chatId, userId=$userId")
          NotFound(chatNotFound)
        })
  }

  def deleteDraft(chatId: String, emailId: String): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>
      val userId = authenticatedRequest.userId
      MDC.put("controllerMethod", "deleteDraft")
      log.info(logRequest(logDeleteDraft))
      log.debug(logRequest(s"$logDeleteDraft: userId=$userId, chatId=$chatId, emailId=$emailId"))

      chatService.deleteDraft(chatId, emailId, userId).map(
        if (_) {
          log.info("The draft was deleted")
          log.debug(s"The draft was deleted: chatId=$chatId, emailId=$emailId, userId=$userId")
          NoContent
        } else {
          log.info(s"$emailNotFound")
          log.debug(s"$emailNotFound. chatId=$chatId, emailId=$emailId, userId=$userId")
          NotFound(emailNotFound)
        })
  }

  def deleteOverseer(chatId: String, oversightId: String): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>
      val userId = authenticatedRequest.userId
      MDC.put("controllerMethod", "deleteOverseer")
      log.info(logRequest(logDeleteOverseer))
      log.debug(logRequest(s"$logDeleteOverseer: userId=$userId, chatId=$chatId, oversightId=$oversightId"))

      chatService.deleteOverseer(chatId, oversightId, userId)
        .map(if (_) {
          log.info("The overseer was deleted")
          log.debug(s"The overseer was deleted: chatId=$chatId, oversightId=$oversightId, userId=$userId")
          NoContent
        } else {
          log.info(s"$overseerNotFound")
          log.debug(s"$overseerNotFound. chatId=$chatId, oversightId=$oversightId, userId=$userId")
          NotFound(overseerNotFound)
        })
  }

  def postOverseers(chatId: String): Action[JsValue] = {
    authenticatedUserAction.async(parse.json) { authenticatedRequest =>
      val jsonValue = authenticatedRequest.request.body
      val userId = authenticatedRequest.userId
      MDC.put("controllerMethod", "postOverseers")
      log.info(logRequest(logPostOverseers))
      log.debug(logRequest(s"$logPostOverseers: userId=$userId, chatId=$chatId, requestBody=$jsonValue"))

      jsonValue.validate[Set[PostOverseerDTO]].fold(
        errors => {
          log.info(invalidJsonSet(PostOverseerDTO, errors))
          log.debug(invalidJsonSet(PostOverseerDTO))
          Future.successful(BadRequest(JsError.toJson(errors)))
        },
        postOverseersDTO => chatService.postOverseers(postOverseersDTO, chatId, authenticatedRequest.userId)
          .map {
            case Some(result) =>
              log.info("The overseers were posted")
              log.debug(s"The overseers were posted: userId=$userId, chatId=$chatId, DTO=$result")
              Ok(Json.toJson(result))
            case None =>
              log.info(s"$chatNotFound")
              log.debug(s"$chatNotFound. chatId=$chatId, userId=$userId")
              NotFound(chatNotFound)
          })
    }
  }

  def getOversights: Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>
      val userId = authenticatedRequest.userId
      MDC.put("controllerMethod", "getOversights")
      log.info(logRequest(logGetOversights))
      log.debug(logRequest(s"$logGetOversights: userId=$userId"))

      chatService.getOversights(userId)
        .map {
          case Some(oversightDTO) =>
            val oversightsPreview = Json.obj("oversightsPreview" -> Json.toJson(oversightDTO))
            val metadata = Json.obj("_metadata" ->
              Json.obj("links" ->
                Json.obj(
                  "overseeing" -> routes.ChatController.getOverseeings(Page(DEFAULT_PAGE), PerPage(DEFAULT_PER_PAGE),
                    Sort(DEFAULT_SORT, DefaultOrder))
                    .absoluteURL(authenticatedRequest.secure)(authenticatedRequest.request),
                  "overseen" -> routes.ChatController.getOverseens(Page(DEFAULT_PAGE), PerPage(DEFAULT_PER_PAGE),
                    Sort(DEFAULT_SORT, DefaultOrder))
                    .absoluteURL(authenticatedRequest.secure)(authenticatedRequest.request))))
            log.info("The oversights were retrived")
            log.debug(s"The oversights were retrived: ${(oversightsPreview ++ metadata).toString}")
            Ok(oversightsPreview ++ metadata)
          case None =>
            log.info(s"$oversightsNotFound")
            log.debug(s"$oversightsNotFound: userId=$userId")
            NotFound(oversightsNotFound)
        }
  }

  def getOverseeings(page: Page, perPage: PerPage,
    sort: Sort): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>
      val userId = authenticatedRequest.userId
      MDC.put("controllerMethod", "getOverseeings")
      log.info(logRequest(logGetOverseeings))
      log.debug(logRequest(s"$logGetOverseeings: userId=$userId, page=${page.value}, perPage=${perPage.value}," +
        s" sort=$sort"))

      if (sort.sortBy == SORT_BY_DATE || sort.sortBy == DEFAULT_SORT)
        chatService.getOverseeings(page, perPage, sort, authenticatedRequest.userId).map {
          case Some((seqChatOverseeingDTO, totalCount, lastPage)) =>
            val overseeings = Json.obj("overseeings" -> Json.toJson(seqChatOverseeingDTO))

            val metadata = Json.obj("_metadata" -> Json.toJsObject(PaginationDTO(
              totalCount,
              PageLinksDTO(
                self = makeGetOverseeingsLink(page, perPage, sort, authenticatedRequest),
                first = makeGetOverseeingsLink(Page(0), perPage, sort, authenticatedRequest),
                previous = if (page == 0) None
                else Some(makeGetOverseeingsLink(page - 1, perPage, sort, authenticatedRequest)),
                next = if (page >= lastPage) None
                else Some(makeGetOverseeingsLink(page + 1, perPage, sort, authenticatedRequest)),
                last = makeGetOverseeingsLink(lastPage, perPage, sort, authenticatedRequest)))))
            log.info("The overseeings were retrived")
            log.debug(s"The overseeings were retrived: ${(overseeings ++ metadata).toString}")
            Ok(overseeings ++ metadata)
          case None =>
            log.error(serviceReturn(logNonePagError))
            log.debug(serviceReturn(s"$logNonePagError: page=$page, perPage=$perPage"))
            log.error(serviceReturn(internalError))
            InternalServerError(internalError)
        }
      else {
        log.info(badRequest(invalidSortBy))
        log.debug(s"${badRequest(invalidSortBy)}: sort: $sort")
        Future.successful(BadRequest(invalidSortBy))
      }
  }

  def getOverseens(page: Page, perPage: PerPage,
    sort: Sort): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>
      val userId = authenticatedRequest.userId
      MDC.put("controllerMethod", "getOverseens")
      log.info(logRequest(logGetOverseens))
      log.debug(logRequest(s"$logGetOverseens: userId=$userId, page=${page.value}, perPage=${perPage.value}," +
        s" sort=$sort"))

      if (sort.sortBy == SORT_BY_DATE || sort.sortBy == DEFAULT_SORT)
        chatService.getOverseens(page, perPage, sort, authenticatedRequest.userId).map {
          case Some((seqChatOverseenDTO, totalCount, lastPage)) =>
            val overseens = Json.obj("overseens" -> Json.toJson(seqChatOverseenDTO))

            val metadata = Json.obj("_metadata" -> Json.toJsObject(PaginationDTO(
              totalCount,
              PageLinksDTO(
                self = makeGetOverseensLink(page, perPage, sort, authenticatedRequest),
                first = makeGetOverseensLink(Page(0), perPage, sort, authenticatedRequest),
                previous = if (page == 0) None
                else Some(makeGetOverseensLink(page - 1, perPage, sort, authenticatedRequest)),
                next = if (page >= lastPage) None
                else Some(makeGetOverseensLink(page + 1, perPage, sort, authenticatedRequest)),
                last = makeGetOverseensLink(lastPage, perPage, sort, authenticatedRequest)))))
            log.info("The overseens were retrived")
            log.debug(s"The overseens were retrived: ${(overseens ++ metadata).toString}")
            Ok(overseens ++ metadata)
          case None =>
            log.error(serviceReturn(logNonePagError))
            log.debug(serviceReturn(s"$logNonePagError: page=$page, perPage=$perPage"))
            log.error(serviceReturn(internalError))
            InternalServerError(internalError)
        }
      else {
        log.info(badRequest(invalidSortBy))
        log.debug(s"${badRequest(invalidSortBy)}: sort: $sort")
        Future.successful(BadRequest(invalidSortBy))
      }
  }

  def postAttachment(chatId: String, emailId: String): Action[MultipartFormData[TemporaryFile]] =
    authenticatedUserAction.async(parse.multipartFormData) {
      implicit authenticatedRequest =>
        val userId = authenticatedRequest.userId
        MDC.put("controllerMethod", "postAttachment")
        log.info(logRequest(logPostAttachment))
        log.debug(logRequest(s"$logPostAttachment: userId=$userId, chatId=$chatId, emailId=$emailId"))

        authenticatedRequest.body.file("attachment") match {
          case Some(FilePart(_, filename, contentType, ref)) =>
            chatService.postAttachment(chatId, emailId, userId, filename, contentType, FileIO.fromPath(ref.path)).map {
              case Some(attachmentId) =>
                log.info("The attachment was posted")
                log.debug(s"The attachment was posted. userId=$userId, chatId=$chatId, emailId=$emailId, attachmentId=$attachmentId")
                Ok(Json.obj("attachmentId" -> attachmentId))
              case None =>
                log.info(s"$chatNotFound")
                log.debug(s"$chatNotFound. chatId=$chatId, userId=$userId")
                NotFound(chatNotFound)
            }
          case None =>
            log.info(s"$missingAttachment")
            log.debug(s"$missingAttachment. userId=$userId, chatId=$chatId, emailId=$emailId")
            Future.successful(BadRequest(missingAttachment))
        }
    }

  def getAttachment(chatId: String, emailId: String, attachmentId: String): Action[AnyContent] =
    authenticatedUserAction.async {
      authenticatedRequest =>
        chatService.getAttachment(chatId, emailId, attachmentId, authenticatedRequest.userId).map {
          case Some(AttachmentDTO(source, optionContentType, filename)) =>
            optionContentType match {
              case Some(contentType) => Ok.chunked(source).as(contentType)
              case None => Ok.chunked(source)
            }
          case None =>
            NotFound(chatNotFound)
        }
    }
}

object ChatController {
  def makeGetChatsLink(mailbox: Mailbox, page: Page, perPage: PerPage, sort: Sort,
    auth: AuthenticatedUser[Any]): String =
    routes.ChatController.getChats(mailbox, page, perPage, sort).absoluteURL(auth.secure)(auth.request)

  def makeGetChatLink(chatId: String, page: Page, perPage: PerPage, sort: Sort,
    auth: AuthenticatedUser[Any]): String =
    routes.ChatController.getChat(chatId, page, perPage, sort).absoluteURL(auth.secure)(auth.request)

  def makeGetEmailLink(chatId: String, emailId: String, auth: AuthenticatedUser[Any]): String =
    routes.ChatController.getEmail(chatId, emailId).absoluteURL(auth.secure)(auth.request)

  def makeGetOverseersLink(chatId: String, page: Page, perPage: PerPage, sort: Sort,
    auth: AuthenticatedUser[Any]): String =
    routes.ChatController.getOverseers(chatId, page, perPage, sort).absoluteURL(auth.secure)(auth.request)

  def makeGetOverseeingsLink(page: Page, perPage: PerPage, sort: Sort, auth: AuthenticatedUser[Any]): String =
    routes.ChatController.getOverseeings(page, perPage, sort).absoluteURL(auth.secure)(auth.request)

  def makeGetOverseensLink(page: Page, perPage: PerPage, sort: Sort, auth: AuthenticatedUser[Any]): String =
    routes.ChatController.getOverseens(page, perPage, sort).absoluteURL(auth.secure)(auth.request)

}