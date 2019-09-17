package controllers

import model.dtos.PatchChatDTO.{ MoveToTrash, Restore }
import model.dtos._
import model.types.Mailbox._
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
    "return Json for inbox" in {
      val chatPreviewDTO = genChatPreviewDTO.sample.value

      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getChats(Inbox, *)
        .returns(Future.successful(Seq(chatPreviewDTO)))

      val result: Future[Result] = chatController.getChats(Inbox).apply(FakeRequest())
      val json: JsValue = contentAsJson(result)
      json mustBe Json.toJson(Seq(chatPreviewDTO))
    }

    "return Json for sent" in {
      val chatPreviewDTO = genChatPreviewDTO.sample.value
      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getChats(Sent, *)
        .returns(Future.successful(Seq(chatPreviewDTO)))

      val result: Future[Result] = chatController.getChats(Sent).apply(FakeRequest())
      val json: JsValue = contentAsJson(result)
      json mustBe Json.toJson(Seq(chatPreviewDTO))
    }

    "return Json for trash" in {
      val chatPreviewDTO = genChatPreviewDTO.sample.value
      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getChats(Trash, *)
        .returns(Future.successful(Seq(chatPreviewDTO)))

      val result: Future[Result] = chatController.getChats(Trash).apply(FakeRequest())
      val json: JsValue = contentAsJson(result)
      json mustBe Json.toJson(Seq(chatPreviewDTO))
    }

    "return Json for drafts" in {
      val chatPreviewDTO = genChatPreviewDTO.sample.value
      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getChats(Drafts, *)
        .returns(Future.successful(Seq(chatPreviewDTO)))

      val result: Future[Result] = chatController.getChats(Drafts).apply(FakeRequest())
      val json: JsValue = contentAsJson(result)
      json mustBe Json.toJson(Seq(chatPreviewDTO))
    }

  }

  "ChatController#getChat" should {
    "return Json for ChatDTO" in {
      val chatDTO = genChatDTO.sample.value

      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getChat(*, *)
        .returns(Future.successful(Some(chatDTO)))

      val result: Future[Result] = chatController.getChat(chatDTO.chatId).apply(FakeRequest())
      val expectedResult = Json.toJson(chatDTO)

      result.map(_ mustBe Ok(expectedResult))
    }

    "return NotFound" in {
      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getChat(*, *)
        .returns(Future.successful(None))

      val result: Future[Result] = chatController.getChat(genUUID.sample.value).apply(FakeRequest())
      result.map(_ mustBe NotFound)
    }

  }

  "ChatController#postChat" should {
    "return Json with the chat received plus a new chatId and a new emailId" in {

      val (chatController, mockChatService) = getControllerAndServiceMock

      val createChatDTO = genCreateChatDTOption.sample.value.copy(chatId = None)
      val chatId = genUUID.sample.value
      val createChatDTOWithId = createChatDTO.copy(chatId = Some(chatId))

      mockChatService.postChat(*, *)
        .returns(Future.successful(createChatDTOWithId))

      val chatJsonRequest = Json.toJson(createChatDTO)

      val chatJsonResponse = Json.toJson(createChatDTOWithId)

      val request = FakeRequest(POST, "/chats")
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(chatJsonRequest)

      val result: Future[Result] = chatController.postChat.apply(request)

      val json = contentAsJson(result)

      status(result) mustBe OK
      json mustBe chatJsonResponse
    }

    "return 400 Bad Request if any of the email addresses is not a valid address" in {
      val (chatController, _) = getControllerAndServiceMock

      val gencreateChatDTO = genCreateChatDTOption.sample.value.copy(chatId = None)
      val invalidAddress = genString.sample.value

      val chatWithInvalidFromAddress = Json.toJson(gencreateChatDTO.copy(
        email = gencreateChatDTO.email.copy(from = Some(invalidAddress))))

      val request = FakeRequest(POST, "/chats")
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(chatWithInvalidFromAddress)

      val result: Future[Result] = chatController.postChat.apply(request)

      status(result) mustBe BAD_REQUEST
    }
  }

  "ChatController#postEmail" should {
    "return Json with the email received plus the chat data and a new emailId" in {

      val (chatController, mockChatService) = getControllerAndServiceMock
      val upsertEmailDTO = genUpsertEmailDTOption.sample.value.copy(emailId = None)
      val emailId = genUUID.sample.value
      val chatId = genUUID.sample.value
      val subject = genString.sample.value
      val createEmailDTOWithId = upsertEmailDTO.copy(emailId = Some(emailId))

      val createChatDTO = CreateChatDTO(Some(chatId), Some(subject), createEmailDTOWithId)

      mockChatService.postEmail(*, *, *)
        .returns(Future.successful(Some(createChatDTO)))

      val emailJsonRequest = Json.toJson(upsertEmailDTO)

      val chatJsonResponse = Json.toJson(createChatDTO)

      val request = FakeRequest()
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(emailJsonRequest)

      val result: Future[Result] = chatController.postEmail("").apply(request)

      val json = contentAsJson(result)

      status(result) mustBe OK
      json mustBe chatJsonResponse
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

      val json = contentAsJson(result)

      status(result) mustBe NOT_FOUND
      json mustBe chatNotFound
    }

  }

  "ChatController#patchChat" should {
    "return Ok and the request body if the response from the service is Some(MoveToTrash)" in {

      val mockChatService = mock[ChatService]
      mockChatService.patchChat(*, *, *)
        .returns(Future.successful(Some(MoveToTrash)))

      val controller = new ChatController(cc, mockChatService, new FakeAuthenticatedUserAction)

      val patchChatJsonRequest = Json.parse("""{"command": "moveToTrash"}""")

      val request = FakeRequest(PATCH, "/chats/825ee397-f36e-4023-951e-89d6e43a8e7d")
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(patchChatJsonRequest)

      val result: Future[Result] = controller.patchChat("825ee397-f36e-4023-951e-89d6e43a8e7d").apply(request)

      status(result) mustBe OK
      contentAsJson(result) mustBe patchChatJsonRequest
    }

    "return Ok and the request body if the response from the service is Some(Restore)" in {

      val mockChatService = mock[ChatService]
      mockChatService.patchChat(*, *, *)
        .returns(Future.successful(Some(Restore)))

      val controller = new ChatController(cc, mockChatService, new FakeAuthenticatedUserAction)

      val patchChatJsonRequest = Json.parse("""{"command": "restore"}""")

      val request = FakeRequest(PATCH, "/chats/825ee397-f36e-4023-951e-89d6e43a8e7d")
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(patchChatJsonRequest)

      val result: Future[Result] = controller.patchChat("825ee397-f36e-4023-951e-89d6e43a8e7d").apply(request)

      status(result) mustBe OK
      contentAsJson(result) mustBe patchChatJsonRequest
    }

    "return NotFound if the response from the service is None" in {

      val mockChatService = mock[ChatService]
      mockChatService.patchChat(*, *, *)
        .returns(Future.successful(None))

      val controller = new ChatController(cc, mockChatService, new FakeAuthenticatedUserAction)

      val patchChatJsonRequest = Json.parse("""{"command": "restore"}""")

      val request = FakeRequest(PATCH, "/chats/825ee397-f36e-4023-951e-89d6e43a8e7d")
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(patchChatJsonRequest)

      val result: Future[Result] = controller.patchChat("825ee397-f36e-4023-951e-89d6e43a8e7d").apply(request)

      result.map(_ mustBe NotFound)
    }

    "return BadRequest if the command is unknown" in {

      val mockChatService = mock[ChatService]

      val controller = new ChatController(cc, mockChatService, new FakeAuthenticatedUserAction)

      val patchChatJsonRequest = Json.parse("""{"command": "unknownCommand"}""")

      val request = FakeRequest(PATCH, "/chats/00000000-0000-0000-0000-000000000000")
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(patchChatJsonRequest)

      val result: Future[Result] = controller.patchChat("825ee397-f36e-4023-951e-89d6e43a8e7d").apply(request)

      result.map(_ mustBe BadRequest)
    }
  }

  "ChatController#patchEmail" should {
    "return Json with the patched email" in {

      val (chatController, mockChatService) = getControllerAndServiceMock

      val emailDTO = EmailDTO("00000000-0000-0000-0000-000000000000", "beatriz@mail.com", Set("joao@mail.com"), Set(),
        Set(), "This is the patched body", "2019-07-26 15:00:00", sent = false, Set())

      mockChatService.patchEmail(*, *, *, *)
        .returns(Future.successful(Some(emailDTO)))

      val emailJsonRequest = Json.parse(
        """{
          |   "to": ["joao@mail.com"],
          |   "cc": [],
          |   "body": "This is the patched body"
          |}""".stripMargin)

      val emailJsonResponse = Json.parse(
        """{
          |  "emailId": "00000000-0000-0000-0000-000000000000",
          |   "from": "beatriz@mail.com",
          |   "to": ["joao@mail.com"],
          |   "bcc": [],
          |   "cc": [],
          |   "body": "This is the patched body",
          |   "date": "2019-07-26 15:00:00",
          |   "sent": false,
          |   "attachments": []
          |}""".stripMargin)

      val request = FakeRequest(PATCH, "/chats/00000000-0000-0000-0000-000000000000/emails/00000000-0000-0000-0000-000000000000")
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(emailJsonRequest)

      val result: Future[Result] = chatController
        .patchEmail("00000000-0000-0000-0000-000000000000", "00000000-0000-0000-0000-000000000000").apply(request)

      val json = contentAsJson(result)

      status(result) mustBe OK
      json mustBe emailJsonResponse
    }

    "return 400 Bad Request if any of the email addresses is not a valid address" in {
      val (chatController, mockChatService) = getControllerAndServiceMock

      val emailWithInvalidToAddress = Json.parse(
        """{
          |   "to": ["joaomail.com"],
          |   "body": "This is the body"
          |}""".stripMargin)

      val request = FakeRequest(PATCH, "/chats/00000000-0000-0000-0000-000000000000/emails/00000000-0000-0000-0000-000000000000")
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(emailWithInvalidToAddress)

      val result: Future[Result] = chatController
        .patchEmail("00000000-0000-0000-0000-000000000000", "00000000-0000-0000-0000-000000000000").apply(request)

      status(result) mustBe BAD_REQUEST
    }
  }

  "ChatController#getEmail" should {
    "return Json for some ChatDTO with one email" in {
      val (chatController, mockChatService) = getControllerAndServiceMock
      val chatId = "6c664490-eee9-4820-9eda-3110d794a998"
      val emailId = "f15967e6-532c-40a6-9335-064d884d4906"

      val responseChatDto = ChatDTO(chatId, "Subject", Set("address1", "address2"), Set(OverseersDTO("address1", Set("address3"))),
        Seq(EmailDTO(emailId, "address1", Set("address2"), Set(), Set(), "This is the body", "2019-07-19 10:00:00", sent = true,
          Set("65aeedbf-aedf-4b1e-b5d8-b348309a14e0"))))

      mockChatService.getEmail(*, *, *)
        .returns(Future.successful(Some(responseChatDto)))

      chatController.getEmail(chatId, emailId).apply(FakeRequest()).map(
        result => result mustBe Ok(Json.toJson(responseChatDto)))
    }

    "return NotFound if service response is None" in {
      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getEmail(*, *, *)
        .returns(Future.successful(None))

      chatController.getEmail("00000000-0000-0000-0000-000000000000", "00000000-0000-0000-0000-000000000000").apply(FakeRequest())
        .map(_ mustBe NotFound)
    }
  }

  "ChatController#deleteChat" should {
    "return NoContent if the response from the service is true" in {
      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.deleteChat(*, *)
        .returns(Future.successful(true))

      chatController.deleteChat("303c2b72-304e-4bac-84d7-385acb64a616").apply(FakeRequest())
        .map(result => result mustBe NoContent)
    }

    "return NotFound if the response from the service is NOT true" in {
      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.deleteChat(*, *)
        .returns(Future.successful(false))

      chatController.deleteChat("825ee397-f36e-4023-951e-89d6e43a8e7d").apply(FakeRequest())
        .map(result => result mustBe NotFound)
    }
  }

  "ChatController#deleteDraft" should {
    "return NoContent if the response from the service is true" in {

      val mockChatService = mock[ChatService]
      mockChatService.deleteDraft(*, *, *)
        .returns(Future.successful(true))

      val controller = new ChatController(cc, mockChatService, new FakeAuthenticatedUserAction)

      controller.deleteDraft("303c2b72-304e-4bac-84d7-385acb64a616", "f203c270-5f37-4437-956a-3cf478f5f28f")
        .apply(FakeRequest())
        .map(result => result mustBe NoContent)
    }

    "return NotFound if the response from the service is NOT true" in {

      val mockChatService = mock[ChatService]
      mockChatService.deleteDraft(*, *, *)
        .returns(Future.successful(false))

      val controller = new ChatController(cc, mockChatService, new FakeAuthenticatedUserAction)

      controller.deleteDraft("825ee397-f36e-4023-951e-89d6e43a8e7d", "f203c270-5f37-4437-956a-3cf478f5f28f")
        .apply(FakeRequest())
        .map(result => result mustBe NotFound)
    }
  }

}