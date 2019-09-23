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
      val createChatDTOWithId = createChatDTO.copy(chatId = Some(genUUID.sample.value))

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

      result.map(_ mustBe NotFound)
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

      result.map(_ mustBe BadRequest)
    }
  }

  "ChatController#patchEmail" should {
    "return Json with the patched email" in {

      val (chatController, mockChatService) = getControllerAndServiceMock

      val emailDTO = genEmailDTO.sample.value

      mockChatService.patchEmail(*, *, *, *)
        .returns(Future.successful(Some(emailDTO)))

      val emailJsonRequest = Json.toJson(genUpsertEmailDTOption.sample.value)

      val emailJsonResponse = Json.toJson(emailDTO)

      val chatId = genUUID.sample.value
      val emailId = genUUID.sample.value

      val request = FakeRequest(PATCH, s"/chats/$chatId/emails/$emailId")
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(emailJsonRequest)

      val result: Future[Result] = chatController
        .patchEmail(chatId, emailId).apply(request)

      val json = contentAsJson(result)

      status(result) mustBe OK
      json mustBe emailJsonResponse
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

      chatController.getEmail(genUUID.sample.value, genUUID.sample.value).apply(FakeRequest()).map(
        result => result mustBe Ok(Json.toJson(responseChatDto)))
    }

    "return NotFound if service response is None" in {
      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.getEmail(*, *, *)
        .returns(Future.successful(None))

      chatController.getEmail(genUUID.sample.value, genUUID.sample.value).apply(FakeRequest())
        .map(_ mustBe NotFound)
    }
  }

  "ChatController#deleteChat" should {
    "return NoContent if the response from the service is true" in {
      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.deleteChat(*, *)
        .returns(Future.successful(true))

      val chatId = genUUID.sample.value

      chatController.deleteChat(chatId).apply(FakeRequest())
        .map(result => result mustBe NoContent)
    }

    "return NotFound if the response from the service is NOT true" in {
      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.deleteChat(*, *)
        .returns(Future.successful(false))

      val chatId = genUUID.sample.value

      chatController.deleteChat(chatId).apply(FakeRequest())
        .map(result => result mustBe NotFound)
    }
  }

  "ChatController#deleteDraft" should {
    "return NoContent if the response from the service is true" in {

      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.deleteDraft(*, *, *)
        .returns(Future.successful(true))

      chatController.deleteDraft(genUUID.sample.value, genUUID.sample.value)
        .apply(FakeRequest())
        .map(result => result mustBe NoContent)
    }

    "return NotFound if the response from the service is NOT true" in {

      val (chatController, mockChatService) = getControllerAndServiceMock
      mockChatService.deleteDraft(*, *, *)
        .returns(Future.successful(false))

      chatController.deleteDraft(genUUID.sample.value, genUUID.sample.value)
        .apply(FakeRequest())
        .map(result => result mustBe NotFound)
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

      val json = contentAsJson(result)

      status(result) mustBe OK
      json mustBe Json.toJson(setPostOverseerDTO)
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

}