package controllers

import javax.inject._
import model.dtos._
import play.api.mvc._
import play.api.libs.json._
import services.ChatService
import utils.Jsons._
import model.types._
import model.types.Sort._
import repositories.RepUtils.RepConstants._
import repositories.RepUtils.types.OrderBy.DefaultOrder

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class ChatController @Inject() (implicit val ec: ExecutionContext, cc: ControllerComponents, chatService: ChatService,
  authenticatedUserAction: AuthenticatedUserAction)
  extends AbstractController(cc) {

  def getChat(chatId: String, page: Page, perPage: PerPage,
    sort: Sort): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>

      if (sort.sortBy == SORT_BY_DATE || sort.sortBy == DEFAULT_SORT)
        chatService.getChat(chatId, page, perPage, sort, authenticatedRequest.userId).map {
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
            Ok(chat ++ metadata)
          case Left(`chatNotFound`) => NotFound(chatNotFound)
          case Left(_) => InternalServerError(internalError)
        }
      else Future.successful(BadRequest(invalidSortBy))
  }

  def getChats(mailbox: Mailbox, page: Page, perPage: PerPage,
    sort: Sort): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>

      if (sort.sortBy == SORT_BY_DATE || sort.sortBy == DEFAULT_SORT)
        chatService.getChats(mailbox, page, perPage, sort, authenticatedRequest.userId)
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
              Ok(chats ++ metadata)
            case None => InternalServerError(internalError)
          }
      else Future.successful(BadRequest(invalidSortBy))
  }

  /**
   * Gets the user's overseers for the given chat
   * @param chatId The chat's Id
   * @return A postOverseersDTO that contains the address and oversightId for each overseear or 404 NotFound
   */
  def getOverseers(chatId: String, page: Page, perPage: PerPage,
    sort: Sort): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>

      if (sort.sortBy == SORT_BY_ADDRESS || sort.sortBy == DEFAULT_SORT)
        chatService.getOverseers(chatId, page, perPage, sort, authenticatedRequest.userId).map {
          case Right((seqPostOverseerDTO, totalCount, lastPage)) =>
            val chats = Json.obj("overseers" -> Json.toJson(seqPostOverseerDTO))

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
            Ok(chats ++ metadata)
          case Left(`chatNotFound`) => NotFound(chatNotFound)
          case Left(_) => InternalServerError(internalError)
        }
      else Future.successful(BadRequest(invalidSortBy))
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
                  "overseeing" -> routes.ChatController.getOverseeings(Page(DEFAULT_PAGE), PerPage(DEFAULT_PER_PAGE),
                    Sort(DEFAULT_SORT, DefaultOrder))
                    .absoluteURL(authenticatedRequest.secure)(authenticatedRequest.request),
                  "overseen" -> routes.ChatController.getOverseens(Page(DEFAULT_PAGE), PerPage(DEFAULT_PER_PAGE),
                    Sort(DEFAULT_SORT, DefaultOrder))
                    .absoluteURL(authenticatedRequest.secure)(authenticatedRequest.request))))
            Ok(oversightsPreview ++ metadata)
          case None => NotFound(oversightsNotFound)
        }
  }

  def getOverseeings(page: Page, perPage: PerPage,
    sort: Sort): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>

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
            Ok(overseeings ++ metadata)
          case None => InternalServerError(internalError)
        }
      else Future.successful(BadRequest(invalidSortBy))
  }

  def getOverseens(page: Page, perPage: PerPage,
    sort: Sort): Action[AnyContent] = authenticatedUserAction.async {
    authenticatedRequest =>

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
            Ok(overseens ++ metadata)
          case None => InternalServerError(internalError)
        }
      else Future.successful(BadRequest(invalidSortBy))
  }

  //region Auxiliary Methods
  def makeGetChatsLink(mailbox: Mailbox, page: Page, perPage: PerPage, sort: Sort,
    auth: AuthenticatedUser[AnyContent]): String =
    routes.ChatController.getChats(mailbox, page, perPage, sort).absoluteURL(auth.secure)(auth.request)

  def makeGetChatLink(chatId: String, page: Page, perPage: PerPage, sort: Sort,
    auth: AuthenticatedUser[AnyContent]): String =
    routes.ChatController.getChat(chatId, page, perPage, sort).absoluteURL(auth.secure)(auth.request)

  def makeGetOverseersLink(chatId: String, page: Page, perPage: PerPage, sort: Sort,
    auth: AuthenticatedUser[AnyContent]): String =
    routes.ChatController.getOverseers(chatId, page, perPage, sort).absoluteURL(auth.secure)(auth.request)

  def makeGetOverseeingsLink(page: Page, perPage: PerPage, sort: Sort, auth: AuthenticatedUser[AnyContent]): String =
    routes.ChatController.getOverseeings(page, perPage, sort).absoluteURL(auth.secure)(auth.request)

  def makeGetOverseensLink(page: Page, perPage: PerPage, sort: Sort, auth: AuthenticatedUser[AnyContent]): String =
    routes.ChatController.getOverseens(page, perPage, sort).absoluteURL(auth.secure)(auth.request)
  //endregion

}