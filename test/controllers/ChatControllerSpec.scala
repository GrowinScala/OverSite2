package controllers

import model.dtos.PatchChatDTO.{ ChangeSubject, MoveToTrash, Restore }
import model.dtos._
import model.types._
import model.types.Sort._
import repositories.RepUtils.RepConstants._
import org.scalatestplus.play._
import play.api.libs.json._
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import services.ChatService
import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.OptionValues
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import utils.Jsons._
import utils.TestGenerators._
import org.scalacheck.Gen._
import repositories.RepUtils.types.OrderBy.DefaultOrder

import scala.concurrent.{ ExecutionContext, Future }

class ChatControllerSpec extends PlaySpec with OptionValues with Results with IdiomaticMockito {

  private lazy val appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
  private lazy val injector: Injector = appBuilder.injector()
  implicit val ec: ExecutionContext = injector.instanceOf[ExecutionContext]
  implicit val cc: ControllerComponents = injector.instanceOf[ControllerComponents]
  implicit val fakeAuthenticatedUserAction: AuthenticatedUserAction =
    injector.instanceOf[AuthenticatedUserAction]

  private val LOCALHOST = "localhost:9000"

  def getControllerAndServiceMock: (ChatController, ChatService) = {

    implicit val mockChatService: ChatService = mock[ChatService]
    val chatController = new ChatController()
    (chatController, mockChatService)
  }

  "ChatController#getChats" should {
    def makeGetChatsLink(mailbox: Mailbox, page: Page, perPage: PerPage, sort: Sort): String =
      s"http://localhost/chats?mailbox=${mailbox.value}&page=${page.value.toString}&perPage=${perPage.value.toString}&${
        implicitly[QueryStringBindable[Sort]].unbind("sort", sort)
      }"

    "return the data provided by the service along with the corresponding metadata" in {
      val chatsPreviewDTO = genChatPreviewDTOSeq.sample.value
      val totalCount = choose(0, 10).sample.value
      val page = genPage.sample.value
      val lastPage = page + choose(1, 5).sample.value
      val mailbox = genMailbox.sample.value
      val perPage = genPerPage.sample.value
      val sort = genSort(DEFAULT_SORT).sample.value

      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getChats(*, *, *, *, *, *)
        .returns(Future.successful(Some(chatsPreviewDTO, totalCount, lastPage)))

      val result: Future[Result] = chatController.getChats(mailbox, page, perPage, sort)
        .apply(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe {

        val chats = Json.obj("chats" -> Json.toJson(chatsPreviewDTO))

        val metadata = Json.obj("_metadata" -> Json.toJsObject(PaginationDTO(
          totalCount,
          PageLinksDTO(
            self = makeGetChatsLink(mailbox, page, perPage, sort),
            first = makeGetChatsLink(mailbox, Page(0), perPage, sort),
            previous = Some(makeGetChatsLink(mailbox, page - 1, perPage, sort)),
            next = Some(makeGetChatsLink(mailbox, page + 1, perPage, sort)),
            last = makeGetChatsLink(mailbox, lastPage, perPage, sort)))))
        chats ++ metadata
      }
    }

    """do not return a "previous" link if page is 0 """ in {
      val chatsPreviewDTO = genChatPreviewDTOSeq.sample.value
      val totalCount = choose(0, 10).sample.value
      val page = Page(0)
      val lastPage = page + choose(1, 5).sample.value
      val mailbox = genMailbox.sample.value
      val perPage = genPerPage.sample.value
      val sort = genSort(DEFAULT_SORT).sample.value
      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getChats(*, *, *, *, *, *)
        .returns(Future.successful(Some(chatsPreviewDTO, totalCount, lastPage)))

      val result: Future[Result] = chatController.getChats(mailbox, page, perPage, sort)
        .apply(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe {

        val chats = Json.obj("chats" -> Json.toJson(chatsPreviewDTO))

        val metadata = Json.obj("_metadata" -> Json.toJsObject(PaginationDTO(
          totalCount,
          PageLinksDTO(
            self = makeGetChatsLink(mailbox, page, perPage, sort),
            first = makeGetChatsLink(mailbox, Page(0), perPage, sort),
            previous = None,
            next = Some(makeGetChatsLink(mailbox, page + 1, perPage, sort)),
            last = makeGetChatsLink(mailbox, lastPage, perPage, sort)))))
        chats ++ metadata
      }
    }

    """do not return a "next" link if page is equal or greater than lastPage """ in {
      val chatsPreviewDTO = genChatPreviewDTOSeq.sample.value
      val totalCount = choose(0, 10).sample.value
      val lastPage = genPage.sample.value
      val page = lastPage + choose(0, 5).sample.value
      val mailbox = genMailbox.sample.value
      val perPage = genPerPage.sample.value
      val sort = genSort(DEFAULT_SORT).sample.value

      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getChats(*, *, *, *, *, *)
        .returns(Future.successful(Some(chatsPreviewDTO, totalCount, lastPage)))

      val result: Future[Result] = chatController.getChats(mailbox, page, perPage, sort)
        .apply(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe {

        val chats = Json.obj("chats" -> Json.toJson(chatsPreviewDTO))

        val metadata = Json.obj("_metadata" -> Json.toJsObject(PaginationDTO(
          totalCount,
          PageLinksDTO(
            self = makeGetChatsLink(mailbox, page, perPage, sort),
            first = makeGetChatsLink(mailbox, Page(0), perPage, sort),
            previous = Some(makeGetChatsLink(mailbox, page - 1, perPage, sort)),
            next = None,
            last = makeGetChatsLink(mailbox, lastPage, perPage, sort)))))
        chats ++ metadata
      }
    }

    "return InternalError if the service returns None" in {
      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getChats(*, *, *, *, *, *)
        .returns(Future.successful(None))

      val result: Future[Result] = chatController.getChats(genMailbox.sample.value, genPage.sample.value,
        genPerPage.sample.value, genSort(DEFAULT_SORT).sample.value)
        .apply(FakeRequest())
      status(result) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(result) mustBe internalError
    }

    "return BadRequest if the sort is invalid" in {
      val (chatController, _) = getControllerAndServiceMock

      val result: Future[Result] = chatController.getChats(genMailbox.sample.value, genPage.sample.value,
        genPerPage.sample.value, genString.flatMap(genSort).sample.value)
        .apply(FakeRequest())
      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe invalidSortBy
    }

  }

  "ChatController#getChat" should {
    def makeGetChatLink(chatId: String, page: Page, perPage: PerPage, sort: Sort): String =
      s"http://localhost/chats/$chatId?page=${page.value.toString}&perPage=${perPage.value.toString}&${
        implicitly[QueryStringBindable[Sort]].unbind("sort", sort)
      }"

    "return the data provided by the service along with the corresponding metadata" in {
      val chatDTO = genChatDTO.sample.value
      val totalCount = choose(0, 10).sample.value
      val page = genPage.sample.value
      val lastPage = page + choose(1, 5).sample.value
      val chatId = genUUID.sample.value
      val perPage = genPerPage.sample.value
      val sort = genSort(DEFAULT_SORT).sample.value

      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getChat(*, *, *, *, *, *)
        .returns(Future.successful(Right(chatDTO, totalCount, lastPage)))

      val result: Future[Result] = chatController.getChat(chatId, page, perPage, sort)
        .apply(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe {

        val chat = Json.obj("chat" -> Json.toJson(chatDTO))

        val metadata = Json.obj("_metadata" -> Json.toJsObject(PaginationDTO(
          totalCount,
          PageLinksDTO(
            self = makeGetChatLink(chatId, page, perPage, sort),
            first = makeGetChatLink(chatId, Page(0), perPage, sort),
            previous = Some(makeGetChatLink(chatId, page - 1, perPage, sort)),
            next = Some(makeGetChatLink(chatId, page + 1, perPage, sort)),
            last = makeGetChatLink(chatId, lastPage, perPage, sort)))))
        chat ++ metadata
      }
    }

    """do not return a "previous" link if page is 0 """ in {
      val chatDTO = genChatDTO.sample.value
      val totalCount = choose(0, 10).sample.value
      val page = Page(0)
      val lastPage = page + choose(1, 5).sample.value
      val chatId = genUUID.sample.value
      val perPage = genPerPage.sample.value
      val sort = genSort(DEFAULT_SORT).sample.value

      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getChat(*, *, *, *, *, *)
        .returns(Future.successful(Right(chatDTO, totalCount, lastPage)))

      val result: Future[Result] = chatController.getChat(chatId, page, perPage, sort)
        .apply(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe {

        val chat = Json.obj("chat" -> Json.toJson(chatDTO))

        val metadata = Json.obj("_metadata" -> Json.toJsObject(PaginationDTO(
          totalCount,
          PageLinksDTO(
            self = makeGetChatLink(chatId, page, perPage, sort),
            first = makeGetChatLink(chatId, Page(0), perPage, sort),
            previous = None,
            next = Some(makeGetChatLink(chatId, page + 1, perPage, sort)),
            last = makeGetChatLink(chatId, lastPage, perPage, sort)))))
        chat ++ metadata
      }
    }

    """do not return a "next" link if page is equal or greater than lastPage """ in {
      val chatDTO = genChatDTO.sample.value
      val totalCount = choose(0, 10).sample.value
      val lastPage = genPage.sample.value
      val page = lastPage + choose(0, 5).sample.value
      val chatId = genUUID.sample.value
      val perPage = genPerPage.sample.value
      val sort = genSort(DEFAULT_SORT).sample.value

      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getChat(*, *, *, *, *, *)
        .returns(Future.successful(Right(chatDTO, totalCount, lastPage)))

      val result: Future[Result] = chatController.getChat(chatId, page, perPage, sort)
        .apply(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe {

        val chat = Json.obj("chat" -> Json.toJson(chatDTO))

        val metadata = Json.obj("_metadata" -> Json.toJsObject(PaginationDTO(
          totalCount,
          PageLinksDTO(
            self = makeGetChatLink(chatId, page, perPage, sort),
            first = makeGetChatLink(chatId, Page(0), perPage, sort),
            previous = Some(makeGetChatLink(chatId, page - 1, perPage, sort)),
            next = None,
            last = makeGetChatLink(chatId, lastPage, perPage, sort)))))
        chat ++ metadata
      }
    }

    "return NotFound if that is the service's message" in {
      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getChat(*, *, *, *, *, *)
        .returns(Future.successful(Left(chatNotFound)))

      val result: Future[Result] = chatController.getChat(genUUID.sample.value, genPage.sample.value,
        genPerPage.sample.value, genSort(DEFAULT_SORT).sample.value).apply(FakeRequest())

      status(result) mustBe NOT_FOUND
      contentAsJson(result) mustBe chatNotFound
    }

    "return InternalServerError if the service returns an error message other than chatNotFound" in {
      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getChat(*, *, *, *, *, *)
        .returns(Future.successful(Left(genSimpleJsObj.sample.value)))

      val result: Future[Result] = chatController.getChat(genUUID.sample.value, genPage.sample.value,
        genPerPage.sample.value, genSort(DEFAULT_SORT).sample.value).apply(FakeRequest())

      status(result) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(result) mustBe internalError
    }

    "return BadRequest if the sort is invalid" in {
      val (chatController, _) = getControllerAndServiceMock

      val result: Future[Result] = chatController.getChat(genUUID.sample.value, genPage.sample.value,
        genPerPage.sample.value, genString.flatMap(genSort).sample.value)
        .apply(FakeRequest())
      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe invalidSortBy
    }

  }

  "ChatController#postChat" should {
    "return Json with the chat received plus a new chatId and a new emailId" in {

      val (chatController, mockChatService) = getControllerAndServiceMock

      val createChatDTO = genCreateChatDTOption.sample.value.copy(chatId = None)
      val createChatDTOWithId = createChatDTO.copy(chatId = Some(genUUID.sample.value))

      mockChatService.postChat(*, *)
        .returns(Future.successful(Some(createChatDTOWithId)))

      val chatJsonRequest = Json.toJson(createChatDTO)

      val chatJsonResponse = Json.toJson(createChatDTOWithId)

      val request = FakeRequest(POST, "/chats")
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(chatJsonRequest)

      val result: Future[Result] = chatController.postChat.apply(request)

      status(result) mustBe OK
      contentAsJson(result) mustBe chatJsonResponse
    }

    "return 400 Bad Request if any of the email addresses is not a valid address" in {
      val (chatController, _) = getControllerAndServiceMock

      val genCreateChatDTO = genCreateChatDTOption.sample.value.copy(chatId = None)
      val invalidAddress = genString.sample.value

      val chatWithInvalidFromAddress = Json.toJson(genCreateChatDTO.copy(
        email = genCreateChatDTO.email.copy(from = Some(invalidAddress))))

      val request = FakeRequest(POST, "/chats")
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(chatWithInvalidFromAddress)

      val result: Future[Result] = chatController.postChat.apply(request)

      status(result) mustBe BAD_REQUEST
    }

    "return 500 Internal Server Error if the service returns None" in {
      val (chatController, mockChatService) = getControllerAndServiceMock

      mockChatService.postChat(*, *)
        .returns(Future.successful(None))

      val chatJsonRequest = Json.toJson(genCreateChatDTOption.sample.value)

      val request = FakeRequest(POST, "/chats")
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(chatJsonRequest)

      val result: Future[Result] = chatController.postChat.apply(request)

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

  }

  "ChatController#postEmail" should {
    "return Json with the email received plus the chat data and a new emailId" in {

      val (chatController, mockChatService) = getControllerAndServiceMock
      val upsertEmailDTO = genUpsertEmailDTOption.sample.value.copy(emailId = None)
      val emailId = genUUID.sample.value
      val subject = genString.sample.value
      val createEmailDTOWithId = upsertEmailDTO.copy(emailId = Some(emailId))

      val createChatDTO = CreateChatDTO(Some(genUUID.sample.value), Some(subject), createEmailDTOWithId)

      mockChatService.postEmail(*, *, *)
        .returns(Future.successful(Some(createChatDTO)))

      val emailJsonRequest = Json.toJson(upsertEmailDTO)

      val chatJsonResponse = Json.toJson(createChatDTO)

      val request = FakeRequest()
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(emailJsonRequest)

      val result: Future[Result] = chatController.postEmail("").apply(request)

      status(result) mustBe OK
      contentAsJson(result) mustBe chatJsonResponse
    }

    "return 400 Bad Request if any of the email addresses is not a valid address" in {
      val (chatController, _) = getControllerAndServiceMock

      val emailWithInvalidFromAddress = Json.toJson(genUpsertEmailDTOption.sample.value.copy(
        emailId = None, from = Some(genString.sample.value)))

      val request = FakeRequest()
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(emailWithInvalidFromAddress)

      val result: Future[Result] = chatController.postChat.apply(request)

      status(result) mustBe BAD_REQUEST
    }

    "return Notfound if the service return None" in {

      val (chatController, mockChatService) = getControllerAndServiceMock

      mockChatService.postEmail(*, *, *)
        .returns(Future.successful(None))

      val emailJsonRequest = Json.toJson(genUpsertEmailDTOption.sample.value.copy(emailId = None))

      val request = FakeRequest()
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(emailJsonRequest)

      val result: Future[Result] = chatController.postEmail("").apply(request)

      status(result) mustBe NOT_FOUND
      contentAsJson(result) mustBe chatNotFound
    }

  }

  "ChatController#patchChat" should {
    "return Ok and the request body if the response from the service is Some(MoveToTrash)" in {
      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.patchChat(*, *, *)
        .returns(Future.successful(Some(MoveToTrash)))

      val patchChatJsonRequest = Json.parse("""{"command": "moveToTrash"}""")

      val chatId = genUUID.sample.value

      val request = FakeRequest(PATCH, s"/chats/$chatId")
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(patchChatJsonRequest)

      val result: Future[Result] = chatController.patchChat(chatId).apply(request)

      status(result) mustBe OK
      contentAsJson(result) mustBe patchChatJsonRequest
    }

    "return Ok and the request body if the response from the service is Some(Restore)" in {

      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.patchChat(*, *, *)
        .returns(Future.successful(Some(Restore)))

      val patchChatJsonRequest = Json.parse("""{"command": "restore"}""")

      val chatId = genUUID.sample.value

      val request = FakeRequest(PATCH, s"/chats/$chatId")
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(patchChatJsonRequest)

      val result: Future[Result] = chatController.patchChat(chatId).apply(request)

      status(result) mustBe OK
      contentAsJson(result) mustBe patchChatJsonRequest
    }

    "return Ok and the request body if the response from the service is Some(ChangeSubject(New Subject))" in {

      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.patchChat(*, *, *)
        .returns(Future.successful(Some(ChangeSubject("New Subject"))))

      val patchChatJsonRequest = Json.parse("""{"command": "changeSubject", "subject": "New Subject"}""")

      val chatId = genUUID.sample.value

      val request = FakeRequest(PATCH, s"/chats/$chatId")
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(patchChatJsonRequest)

      val result: Future[Result] = chatController.patchChat(chatId).apply(request)

      status(result) mustBe OK
      contentAsJson(result) mustBe patchChatJsonRequest
    }

    "return NotFound if the response from the service is None" in {

      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.patchChat(*, *, *)
        .returns(Future.successful(None))

      val patchChatJsonRequest = Json.parse("""{"command": "restore"}""")

      val chatId = genUUID.sample.value

      val request = FakeRequest(PATCH, s"/chats/$chatId")
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(patchChatJsonRequest)

      val result: Future[Result] = chatController.patchChat(chatId).apply(request)

      status(result) mustBe NOT_FOUND
    }

    "return BadRequest if the command is unknown" in {

      val (chatController, mockChatService) = getControllerAndServiceMock

      val unknownCommand = genString.sample.value

      val patchChatJsonRequest = Json.parse(s"""{"command": "$unknownCommand"}""")

      val chatId = genUUID.sample.value

      val request = FakeRequest(PATCH, s"/chats/$chatId")
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(patchChatJsonRequest)

      val result: Future[Result] = chatController.patchChat(chatId).apply(request)

      status(result) mustBe BAD_REQUEST
    }
  }

  "ChatController#patchEmail" should {
    "return Json with the patched email" in {

      val (chatController, mockChatService) = getControllerAndServiceMock

      val chatId = genUUID.sample.value
      val emailId = genUUID.sample.value

      val emailDTO = genEmailDTO(chatId).sample.value

      mockChatService.patchEmail(*, *, *, *, *)
        .returns(Future.successful(Some(emailDTO)))

      val emailJsonRequest = Json.toJson(genUpsertEmailDTOption.sample.value)

      val emailJsonResponse = Json.toJson(emailDTO)

      val request = FakeRequest(PATCH, s"/chats/$chatId/emails/$emailId")
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(emailJsonRequest)

      val result: Future[Result] = chatController
        .patchEmail(chatId, emailId).apply(request)

      status(result) mustBe OK
      contentAsJson(result) mustBe emailJsonResponse
    }

    "return 400 Bad Request if any of the email addresses is not a valid address" in {
      val (chatController, mockChatService) = getControllerAndServiceMock

      val emailWithInvalidToAddress = Json.toJson(genUpsertEmailDTOption.sample.value
        .copy(to = Some(Set(genString.sample.value))))

      val chatId = genUUID.sample.value
      val emailId = genUUID.sample.value

      val request = FakeRequest(PATCH, s"/chats/$chatId/emails/$emailId")
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(emailWithInvalidToAddress)

      val result: Future[Result] = chatController
        .patchEmail(chatId, emailId).apply(request)

      status(result) mustBe BAD_REQUEST
    }
  }

  "ChatController#getEmail" should {
    "return Json for some ChatDTO with one email" in {
      val (chatController, mockChatService) = getControllerAndServiceMock

      val responseChatDto = genChatDTO.sample.value

      mockChatService.getEmail(*, *, *)
        .returns(Future.successful(Some(responseChatDto)))

      val result = chatController.getEmail(genUUID.sample.value, genUUID.sample.value).apply(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(responseChatDto)
    }

    "return NotFound if service response is None" in {
      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getEmail(*, *, *)
        .returns(Future.successful(None))

      val result = chatController.getEmail(genUUID.sample.value, genUUID.sample.value).apply(FakeRequest())

      status(result) mustBe NOT_FOUND
    }
  }

  "ChatController#deleteChat" should {
    "return NoContent if the response from the service is true" in {
      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.deleteChat(*, *)
        .returns(Future.successful(true))

      val chatId = genUUID.sample.value

      val result = chatController.deleteChat(chatId).apply(FakeRequest())
      status(result) mustBe NO_CONTENT
    }

    "return NotFound if the response from the service is NOT true" in {
      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.deleteChat(*, *)
        .returns(Future.successful(false))

      val chatId = genUUID.sample.value

      val result = chatController.deleteChat(chatId).apply(FakeRequest())
      status(result) mustBe NOT_FOUND
    }
  }

  "ChatController#deleteDraft" should {
    "return NoContent if the response from the service is true" in {

      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.deleteDraft(*, *, *)
        .returns(Future.successful(true))

      val result = chatController.deleteDraft(genUUID.sample.value, genUUID.sample.value).apply(FakeRequest())
      status(result) mustBe NO_CONTENT
    }

    "return NotFound if the response from the service is NOT true" in {

      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.deleteDraft(*, *, *)
        .returns(Future.successful(false))

      val result = chatController.deleteDraft(genUUID.sample.value, genUUID.sample.value).apply(FakeRequest())
      status(result) mustBe NOT_FOUND
    }
  }

  "ChatController#postOverseers" should {
    "return Json with the PostOverseerDTO given by the service" in {

      val (chatController, mockChatService) = getControllerAndServiceMock
      val setPostOverseerDTO = genSetPostOverseerDTO.sample.value

      mockChatService.postOverseers(*, *, *)
        .returns(Future.successful(Some(setPostOverseerDTO)))

      val request = FakeRequest()
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(Json.toJson(setPostOverseerDTO))

      val result: Future[Result] = chatController.postOverseers(genUUID.sample.value).apply(request)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(setPostOverseerDTO)
    }

    "return 400 Bad Request if the email address is not a valid address" in {
      val (chatController, _) = getControllerAndServiceMock

      val setPostOverseerDTOWithInvalidAddresses = Json.toJson(genSetPostOverseerDTO.sample.value
        .map(_.copy(address = genString.sample.value)))

      val request = FakeRequest()
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(setPostOverseerDTOWithInvalidAddresses)

      val result: Future[Result] = chatController.postOverseers(genUUID.sample.value).apply(request)

      status(result) mustBe BAD_REQUEST
    }

    "return Notfound if the service returned None" in {

      val (chatController, mockChatService) = getControllerAndServiceMock

      mockChatService.postOverseers(*, *, *)
        .returns(Future.successful(None))

      val request = FakeRequest()
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(Json.toJson(genSetPostOverseerDTO.sample.value))

      val result: Future[Result] = chatController.postOverseers(genUUID.sample.value).apply(request)

      status(result) mustBe NOT_FOUND
      contentAsJson(result) mustBe chatNotFound
    }

  }

  "ChatController#getOverseers" should {
    def makeGetOverseersLink(chatId: String, page: Page, perPage: PerPage, sort: Sort): String =
      s"http://localhost/chats/$chatId/overseers?page=${page.value.toString}&perPage=${perPage.value.toString}&${
        implicitly[QueryStringBindable[Sort]].unbind("sort", sort)
      }"

    "return the data provided by the service along with the corresponding metadata" in {
      val postOverseersDTO = genSeqPostOverseerDTO.sample.value
      val totalCount = choose(0, 10).sample.value
      val page = genPage.sample.value
      val lastPage = page + choose(1, 5).sample.value
      val chatId = genUUID.sample.value
      val perPage = genPerPage.sample.value
      val sort = genSort(DEFAULT_SORT).sample.value

      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getOverseers(*, *, *, *, *)
        .returns(Future.successful(Right(postOverseersDTO, totalCount, lastPage)))

      val result: Future[Result] = chatController.getOverseers(chatId, page, perPage, sort)
        .apply(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe {

        val overseers = Json.obj("overseers" -> Json.toJson(postOverseersDTO))

        val metadata = Json.obj("_metadata" -> Json.toJsObject(PaginationDTO(
          totalCount,
          PageLinksDTO(
            self = makeGetOverseersLink(chatId, page, perPage, sort),
            first = makeGetOverseersLink(chatId, Page(0), perPage, sort),
            previous = Some(makeGetOverseersLink(chatId, page - 1, perPage, sort)),
            next = Some(makeGetOverseersLink(chatId, page + 1, perPage, sort)),
            last = makeGetOverseersLink(chatId, lastPage, perPage, sort)))))
        overseers ++ metadata
      }
    }

    """do not return a "previous" link if page is 0 """ in {
      val postOverseersDTO = genSeqPostOverseerDTO.sample.value
      val totalCount = choose(0, 10).sample.value
      val page = Page(0)
      val lastPage = page + choose(1, 5).sample.value
      val chatId = genUUID.sample.value
      val perPage = genPerPage.sample.value
      val sort = genSort(DEFAULT_SORT).sample.value

      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getOverseers(*, *, *, *, *)
        .returns(Future.successful(Right(postOverseersDTO, totalCount, lastPage)))

      val result: Future[Result] = chatController.getOverseers(chatId, page, perPage, sort)
        .apply(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe {

        val overseers = Json.obj("overseers" -> Json.toJson(postOverseersDTO))

        val metadata = Json.obj("_metadata" -> Json.toJsObject(PaginationDTO(
          totalCount,
          PageLinksDTO(
            self = makeGetOverseersLink(chatId, page, perPage, sort),
            first = makeGetOverseersLink(chatId, Page(0), perPage, sort),
            previous = None,
            next = Some(makeGetOverseersLink(chatId, page + 1, perPage, sort)),
            last = makeGetOverseersLink(chatId, lastPage, perPage, sort)))))
        overseers ++ metadata
      }
    }

    """do not return a "next" link if page is equal or greater than lastPage """ in {
      val postOverseersDTO = genSeqPostOverseerDTO.sample.value
      val totalCount = choose(0, 10).sample.value
      val lastPage = genPage.sample.value
      val page = lastPage + choose(0, 5).sample.value
      val chatId = genUUID.sample.value
      val perPage = genPerPage.sample.value
      val sort = genSort(DEFAULT_SORT).sample.value

      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getOverseers(*, *, *, *, *)
        .returns(Future.successful(Right(postOverseersDTO, totalCount, lastPage)))

      val result: Future[Result] = chatController.getOverseers(chatId, page, perPage, sort)
        .apply(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe {

        val overseers = Json.obj("overseers" -> Json.toJson(postOverseersDTO))

        val metadata = Json.obj("_metadata" -> Json.toJsObject(PaginationDTO(
          totalCount,
          PageLinksDTO(
            self = makeGetOverseersLink(chatId, page, perPage, sort),
            first = makeGetOverseersLink(chatId, Page(0), perPage, sort),
            previous = Some(makeGetOverseersLink(chatId, page - 1, perPage, sort)),
            next = None,
            last = makeGetOverseersLink(chatId, lastPage, perPage, sort)))))
        overseers ++ metadata
      }
    }

    "return BadRequest if that is the service's message" in {
      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getOverseers(*, *, *, *, *)
        .returns(Future.successful(Left(chatNotFound)))

      val result: Future[Result] = chatController.getOverseers(genUUID.sample.value, genPage.sample.value,
        genPerPage.sample.value, genSort(DEFAULT_SORT).sample.value).apply(FakeRequest())

      status(result) mustBe NOT_FOUND
      contentAsJson(result) mustBe chatNotFound
    }

    "return InternalServerError if the service returns an error message other than chatNotFound" in {
      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getOverseers(*, *, *, *, *)
        .returns(Future.successful(Left(genSimpleJsObj.sample.value)))

      val result: Future[Result] = chatController.getOverseers(genUUID.sample.value, genPage.sample.value,
        genPerPage.sample.value, genSort(DEFAULT_SORT).sample.value).apply(FakeRequest())

      status(result) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(result) mustBe internalError
    }

    "return BadRequest if the sort is invalid" in {
      val (chatController, _) = getControllerAndServiceMock

      val result: Future[Result] = chatController.getOverseers(genUUID.sample.value, genPage.sample.value,
        genPerPage.sample.value, genString.flatMap(genSort).sample.value)
        .apply(FakeRequest())
      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe invalidSortBy
    }
  }

  "ChatController#deleteOverseer" should {
    "return NoContent if the response from the service is true" in {

      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.deleteOverseer(*, *, *)
        .returns(Future.successful(true))

      chatController.deleteOverseer(genUUID.sample.value, genUUID.sample.value)
        .apply(FakeRequest())
        .map(result => result mustBe NoContent)
    }

    "return NotFound if the response from the service is NOT true" in {

      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.deleteOverseer(*, *, *)
        .returns(Future.successful(false))

      chatController.deleteOverseer(genUUID.sample.value, genUUID.sample.value)
        .apply(FakeRequest())
        .map(result => result mustBe NotFound)
    }
  }

  "ChatController#getOversights" should {
    "return the DTO sent by the service along with the metadata" in {
      val (chatController, mockChatService) = getControllerAndServiceMock
      val defaultPage = DEFAULT_PAGE
      val defaultPerPage = DEFAULT_PER_PAGE

      val oversightDTO = genOversightDTO.sample.value

      mockChatService.getOversights(*)
        .returns(Future.successful(Some(oversightDTO)))

      val result: Future[Result] = chatController.getOversights.apply(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe {
        val oversightsPreview = Json.obj("oversightsPreview" -> Json.toJson(oversightDTO))
        val metadata = Json.obj("_metadata" ->
          Json.obj("links" ->
            Json.obj(
              "overseeing" ->
                JsString(s"http://localhost/chats/oversights/overseeings?page=$defaultPage&perPage=$defaultPerPage" +
                  s"&sort=$DEFAULT_SORT"),
              "overseen" ->
                JsString(s"http://localhost/chats/oversights/overseens?page=$defaultPage&perPage=$defaultPerPage" +
                  s"&sort=$DEFAULT_SORT"))))
        oversightsPreview ++ metadata
      }
    }

    "return NotFound when the service returns None" in {
      val (chatController, mockChatService) = getControllerAndServiceMock

      mockChatService.getOversights(*)
        .returns(Future.successful(None))

      val result: Future[Result] = chatController.getOversights.apply(FakeRequest())
      status(result) mustBe NOT_FOUND
      contentAsJson(result) mustBe oversightsNotFound
    }
  }

  "ChatController#getOverseeings" should {
    def makeGetOverseeingsLink(page: Page, perPage: PerPage, sort: Sort): String =
      s"http://localhost/chats/oversights/overseeings?page=${page.value.toString}&perPage=${perPage.value.toString}&${
        implicitly[QueryStringBindable[Sort]].unbind("sort", sort)
      }"

    "return the DTO sent by the service along with the metadata" in {
      val seqChatOverseeingDTO = genSeqChatOverseeingDTO.sample.value
      val totalCount = choose(0, 10).sample.value
      val page = genPage.sample.value
      val lastPage = page + choose(1, 5).sample.value
      val chatId = genUUID.sample.value
      val perPage = genPerPage.sample.value
      val sort = genSort(DEFAULT_SORT).sample.value

      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getOverseeings(*, *, *, *)
        .returns(Future.successful(Some((seqChatOverseeingDTO, totalCount, lastPage))))

      val result: Future[Result] = chatController.getOverseeings(page, perPage, sort)
        .apply(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe {

        val overseeings = Json.obj("overseeings" -> Json.toJson(seqChatOverseeingDTO))

        val metadata = Json.obj("_metadata" -> Json.toJsObject(PaginationDTO(
          totalCount,
          PageLinksDTO(
            self = makeGetOverseeingsLink(page, perPage, sort),
            first = makeGetOverseeingsLink(Page(0), perPage, sort),
            previous = Some(makeGetOverseeingsLink(page - 1, perPage, sort)),
            next = Some(makeGetOverseeingsLink(page + 1, perPage, sort)),
            last = makeGetOverseeingsLink(lastPage, perPage, sort)))))
        overseeings ++ metadata
      }
    }

    """do not return a "previous" link if page is 0 """ in {
      val seqChatOverseeingDTO = genSeqChatOverseeingDTO.sample.value
      val totalCount = choose(0, 10).sample.value
      val page = Page(0)
      val lastPage = page + choose(1, 5).sample.value
      val perPage = genPerPage.sample.value
      val sort = genSort(DEFAULT_SORT).sample.value

      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getOverseeings(*, *, *, *)
        .returns(Future.successful(Some((seqChatOverseeingDTO, totalCount, lastPage))))

      val result: Future[Result] = chatController.getOverseeings(page, perPage, sort)
        .apply(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe {

        val overseeings = Json.obj("overseeings" -> Json.toJson(seqChatOverseeingDTO))

        val metadata = Json.obj("_metadata" -> Json.toJsObject(PaginationDTO(
          totalCount,
          PageLinksDTO(
            self = makeGetOverseeingsLink(page, perPage, sort),
            first = makeGetOverseeingsLink(Page(0), perPage, sort),
            previous = None,
            next = Some(makeGetOverseeingsLink(page + 1, perPage, sort)),
            last = makeGetOverseeingsLink(lastPage, perPage, sort)))))
        overseeings ++ metadata
      }
    }

    """do not return a "next" link if page is equal or greater than lastPage """ in {
      val seqChatOverseeingDTO = genSeqChatOverseeingDTO.sample.value
      val totalCount = choose(0, 10).sample.value
      val lastPage = genPage.sample.value
      val page = lastPage + choose(0, 5).sample.value
      val perPage = genPerPage.sample.value
      val sort = genSort(DEFAULT_SORT).sample.value

      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getOverseeings(*, *, *, *)
        .returns(Future.successful(Some((seqChatOverseeingDTO, totalCount, lastPage))))

      val result: Future[Result] = chatController.getOverseeings(page, perPage, sort)
        .apply(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe {

        val overseeings = Json.obj("overseeings" -> Json.toJson(seqChatOverseeingDTO))

        val metadata = Json.obj("_metadata" -> Json.toJsObject(PaginationDTO(
          totalCount,
          PageLinksDTO(
            self = makeGetOverseeingsLink(page, perPage, sort),
            first = makeGetOverseeingsLink(Page(0), perPage, sort),
            previous = Some(makeGetOverseeingsLink(page - 1, perPage, sort)),
            next = None,
            last = makeGetOverseeingsLink(lastPage, perPage, sort)))))
        overseeings ++ metadata
      }
    }

    "return InternalServerError if the service returns None" in {
      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getOverseeings(*, *, *, *)
        .returns(Future.successful(None))

      val result: Future[Result] = chatController.getOverseeings(genPage.sample.value, genPerPage.sample.value,
        genSort(DEFAULT_SORT).sample.value).apply(FakeRequest())

      status(result) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(result) mustBe internalError
    }

    "return BadRequest if the sort is invalid" in {
      val (chatController, _) = getControllerAndServiceMock

      val result: Future[Result] = chatController.getOverseeings(
        genPage.sample.value,
        genPerPage.sample.value, genString.flatMap(genSort).sample.value)
        .apply(FakeRequest())
      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe invalidSortBy
    }
  }

  "ChatController#getOverseens" should {
    def makeGetOverseensLink(page: Page, perPage: PerPage, sort: Sort): String =
      s"http://localhost/chats/oversights/overseens?page=${page.value.toString}&perPage=${perPage.value.toString}&${
        implicitly[QueryStringBindable[Sort]].unbind("sort", sort)
      }"

    "return the DTO sent by the service along with the metadata" in {
      val seqChatOverseenDTO = genSeqChatOverseenDTO.sample.value
      val totalCount = choose(0, 10).sample.value
      val page = genPage.sample.value
      val lastPage = page + choose(1, 5).sample.value
      val chatId = genUUID.sample.value
      val perPage = genPerPage.sample.value
      val sort = genSort(DEFAULT_SORT).sample.value

      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getOverseens(*, *, *, *)
        .returns(Future.successful(Some((seqChatOverseenDTO, totalCount, lastPage))))

      val result: Future[Result] = chatController.getOverseens(page, perPage, sort)
        .apply(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe {

        val overseens = Json.obj("overseens" -> Json.toJson(seqChatOverseenDTO))

        val metadata = Json.obj("_metadata" -> Json.toJsObject(PaginationDTO(
          totalCount,
          PageLinksDTO(
            self = makeGetOverseensLink(page, perPage, sort),
            first = makeGetOverseensLink(Page(0), perPage, sort),
            previous = Some(makeGetOverseensLink(page - 1, perPage, sort)),
            next = Some(makeGetOverseensLink(page + 1, perPage, sort)),
            last = makeGetOverseensLink(lastPage, perPage, sort)))))
        overseens ++ metadata
      }
    }

    """do not return a "previous" link if page is 0 """ in {
      val seqChatOverseenDTO = genSeqChatOverseenDTO.sample.value
      val totalCount = choose(0, 10).sample.value
      val page = Page(0)
      val lastPage = page + choose(1, 5).sample.value
      val perPage = genPerPage.sample.value
      val sort = genSort(DEFAULT_SORT).sample.value

      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getOverseens(*, *, *, *)
        .returns(Future.successful(Some((seqChatOverseenDTO, totalCount, lastPage))))

      val result: Future[Result] = chatController.getOverseens(page, perPage, sort)
        .apply(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe {

        val overseens = Json.obj("overseens" -> Json.toJson(seqChatOverseenDTO))

        val metadata = Json.obj("_metadata" -> Json.toJsObject(PaginationDTO(
          totalCount,
          PageLinksDTO(
            self = makeGetOverseensLink(page, perPage, sort),
            first = makeGetOverseensLink(Page(0), perPage, sort),
            previous = None,
            next = Some(makeGetOverseensLink(page + 1, perPage, sort)),
            last = makeGetOverseensLink(lastPage, perPage, sort)))))
        overseens ++ metadata
      }
    }

    """do not return a "next" link if page is equal or greater than lastPage """ in {
      val seqChatOverseenDTO = genSeqChatOverseenDTO.sample.value
      val totalCount = choose(0, 10).sample.value
      val lastPage = genPage.sample.value
      val page = lastPage + choose(0, 5).sample.value
      val perPage = genPerPage.sample.value
      val sort = genSort(DEFAULT_SORT).sample.value

      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getOverseens(*, *, *, *)
        .returns(Future.successful(Some((seqChatOverseenDTO, totalCount, lastPage))))

      val result: Future[Result] = chatController.getOverseens(page, perPage, sort)
        .apply(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe {

        val overseens = Json.obj("overseens" -> Json.toJson(seqChatOverseenDTO))

        val metadata = Json.obj("_metadata" -> Json.toJsObject(PaginationDTO(
          totalCount,
          PageLinksDTO(
            self = makeGetOverseensLink(page, perPage, sort),
            first = makeGetOverseensLink(Page(0), perPage, sort),
            previous = Some(makeGetOverseensLink(page - 1, perPage, sort)),
            next = None,
            last = makeGetOverseensLink(lastPage, perPage, sort)))))
        overseens ++ metadata
      }
    }

    "return InternalServerError if the service returns None" in {
      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getOverseens(*, *, *, *)
        .returns(Future.successful(None))

      val result: Future[Result] = chatController.getOverseens(genPage.sample.value, genPerPage.sample.value,
        genSort(DEFAULT_SORT).sample.value).apply(FakeRequest())

      status(result) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(result) mustBe internalError
    }

    "return BadRequest if the sort is invalid" in {
      val (chatController, _) = getControllerAndServiceMock

      val result: Future[Result] = chatController.getOverseens(
        genPage.sample.value,
        genPerPage.sample.value, genString.flatMap(genSort).sample.value)
        .apply(FakeRequest())
      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe invalidSortBy
    }
  }

}