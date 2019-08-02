package controllers

import model.dtos.ChatPreviewDTO
import model.types.Mailbox._
import org.scalatestplus.play._
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import services.ChatService
import org.mockito.scalatest.IdiomaticMockito

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }
import scala.util.Random

class ChatControllerSpec extends PlaySpec with Results with IdiomaticMockito {

  private lazy val appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
  private lazy val injector: Injector = appBuilder.injector()
  private val cc: ControllerComponents = Helpers.stubControllerComponents()
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  //region ChatController#getChats
  "ChatController#getChats" should {
    "return Json for inbox" in {
      val mockChatService = mock[ChatService]
      mockChatService.getChats(Inbox, *)
        .returns(Future.successful(Seq(
          ChatPreviewDTO("00000000-0000-0000-0000-000000000000", "Ok", "Ok", "Ok", "Ok"))))

      val fakeAuthenticatedUserAction = new FakeAuthenticatedUserAction

      val controller = new ChatController(cc, mockChatService, fakeAuthenticatedUserAction)
      val result: Future[Result] = controller.getChats("inbox").apply(FakeRequest())
      val json: JsValue = contentAsJson(result)
      json mustBe Json.parse(
        """[{"chatId": "00000000-0000-0000-0000-000000000000","subject": "Ok","lastAddress": "Ok","lastEmailDate": "Ok","contentPreview": "Ok"}]""")
    }
  }

  "ChatController#getChats" should {
    "return Json for sent" in {
      val mockChatService = mock[ChatService]
      mockChatService.getChats(Sent, *)
        .returns(Future.successful(Seq(ChatPreviewDTO("00000000-0000-0000-0000-000000000000", "Ok", "Ok", "Ok", "Ok"))))

      val fakeAuthenticatedUserAction = new FakeAuthenticatedUserAction

      val controller = new ChatController(cc, mockChatService, fakeAuthenticatedUserAction)
      val result: Future[Result] = controller.getChats("sent").apply(FakeRequest())
      val json: JsValue = contentAsJson(result)
      json mustBe Json.parse("""[{"chatId": "00000000-0000-0000-0000-000000000000","subject": "Ok","lastAddress": "Ok","lastEmailDate": "Ok","contentPreview": "Ok"}]""")
    }
  }

  "ChatController#getChats" should {
    "return Json for trash" in {
      val mockChatService = mock[ChatService]
      mockChatService.getChats(Trash, *)
        .returns(Future.successful(Seq(ChatPreviewDTO("00000000-0000-0000-0000-000000000000", "Ok", "Ok", "Ok", "Ok"))))

      val fakeAuthenticatedUserAction = new FakeAuthenticatedUserAction

      val controller = new ChatController(cc, mockChatService, fakeAuthenticatedUserAction)
      val result: Future[Result] = controller.getChats("trash").apply(FakeRequest())
      val json: JsValue = contentAsJson(result)
      json mustBe Json.parse("""[{"chatId": "00000000-0000-0000-0000-000000000000","subject": "Ok","lastAddress": "Ok","lastEmailDate": "Ok","contentPreview": "Ok"}]""")
    }
  }

  "ChatController#getChats" should {
    "return Json for drafts" in {
      val mockChatService = mock[ChatService]
      mockChatService.getChats(Drafts, *)
        .returns(Future.successful(Seq(ChatPreviewDTO("00000000-0000-0000-0000-000000000000", "Ok", "Ok", "Ok", "Ok"))))

      val fakeAuthenticatedUserAction = new FakeAuthenticatedUserAction

      val controller = new ChatController(cc, mockChatService, fakeAuthenticatedUserAction)
      val result: Future[Result] = controller.getChats("drafts").apply(FakeRequest())
      val json: JsValue = contentAsJson(result)
      json mustBe Json.parse("""[{"chatId": "00000000-0000-0000-0000-000000000000","subject": "Ok","lastAddress": "Ok","lastEmailDate": "Ok","contentPreview": "Ok"}]""")
    }
  }

  "ChatController#getChats" should {
    "return Not Found" in {
      val mockChatService = mock[ChatService]
      val fakeAuthenticatedUserAction = new FakeAuthenticatedUserAction

      val controller = new ChatController(cc, mockChatService, fakeAuthenticatedUserAction)
      val result: Future[Result] = controller.getChats(Random.alphanumeric.take(10).mkString).apply(FakeRequest())
      status(result) mustBe NOT_FOUND
    }
  }
  //endregion

}
