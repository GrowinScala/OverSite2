package controllers

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
        email = gencreateChatDTO.email.copy(from = invalidAddress)))

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
      val createEmailDTO = genCreateEmailDTOption.sample.value.copy(emailId = None)
      val emailId = genUUID.sample.value
      val chatId = genUUID.sample.value
      val subject = genString.sample.value
      val createEmailDTOWithId = createEmailDTO.copy(emailId = Some(emailId))

      val createChatDTO = CreateChatDTO(Some(chatId), Some(subject), createEmailDTOWithId)

      mockChatService.postEmail(*, *, *)
        .returns(Future.successful(Some(createChatDTO)))

      val emailJsonRequest = Json.toJson(createEmailDTO)

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

      val emailWithInvalidFromAddress = Json.toJson(genCreateEmailDTOption.sample.value.copy(
        emailId = None, from = genString.sample.value))

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

      val emailJsonRequest = Json.toJson(genCreateEmailDTOption.sample.value.copy(emailId = None))

      val request = FakeRequest()
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(emailJsonRequest)

      val result: Future[Result] = chatController.postEmail("").apply(request)

      val json = contentAsJson(result)

      status(result) mustBe NOT_FOUND
      json mustBe chatNotFound
    }

  }

  "ChatController#moveChatToTrash" should {
    "return NoContent if the response from the service is true" in {

      val (chatController, mockChatService) = getControllerAndServiceMock

      mockChatService.moveChatToTrash(*, *)
        .returns(Future.successful(true))

      val result: Future[Result] = chatController.moveChatToTrash("303c2b72-304e-4bac-84d7-385acb64a616").apply(FakeRequest())

      result.map(_ mustBe NoContent)
    }
    "return NotFound if the response from the service is NOT true" in {
      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.moveChatToTrash(*, *)
        .returns(Future.successful(false))

      val result: Future[Result] = chatController.moveChatToTrash("825ee397-f36e-4023-951e-89d6e43a8e7d").apply(FakeRequest())

      result.map(_ mustBe NotFound)
    }
  }

}