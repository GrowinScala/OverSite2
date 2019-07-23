package controllers

import model.dtos.ChatPreviewDTO
import model.types.Mailbox._
import org.scalatest.mockito.MockitoSugar._
import org.scalatestplus.play._
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import services.ChatService
import org.mockito.Mockito.when
import org.mockito.ArgumentMatchersSugar._

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }
import scala.util.Random

class ChatControllerSpec extends PlaySpec with Results {

  private lazy val appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
  private lazy val injector: Injector = appBuilder.injector()
  private val cc: ControllerComponents = Helpers.stubControllerComponents()
  private val chatService = injector.instanceOf[ChatService]
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  //region ChatController#getChats
  "ChatController#getChats" should {
    "return Json for inbox" in {
      val mockChatService = mock[ChatService]
      when(mockChatService.getChats(eqTo(Inbox), anyInt))
        .thenReturn(Future.successful(Seq(ChatPreviewDTO(1, "Ok", "Ok", "Ok", "Ok"))))

      val controller = new ChatController(cc, mockChatService)
      val result: Future[Result] = controller.getChats("inbox").apply(FakeRequest())
      val json: JsValue = contentAsJson(result)
      json mustBe Json.parse("""[{"chatId": 1,"subject": "Ok","lastAddress": "Ok","lastEmailDate": "Ok","contentPreview": "Ok"}]""")
    }
  }

  "ChatController#getChats" should {
    "return Json for sent" in {
      val mockChatService = mock[ChatService]
      when(mockChatService.getChats(eqTo(Sent), anyInt))
        .thenReturn(Future.successful(Seq(ChatPreviewDTO(1, "Ok", "Ok", "Ok", "Ok"))))

      val controller = new ChatController(cc, mockChatService)
      val result: Future[Result] = controller.getChats("sent").apply(FakeRequest())
      val json: JsValue = contentAsJson(result)
      json mustBe Json.parse("""[{"chatId": 1,"subject": "Ok","lastAddress": "Ok","lastEmailDate": "Ok","contentPreview": "Ok"}]""")
    }
  }

  "ChatController#getChats" should {
    "return Json for trash" in {
      val mockChatService = mock[ChatService]
      when(mockChatService.getChats(eqTo(Trash), anyInt))
        .thenReturn(Future.successful(Seq(ChatPreviewDTO(1, "Ok", "Ok", "Ok", "Ok"))))

      val controller = new ChatController(cc, mockChatService)
      val result: Future[Result] = controller.getChats("trash").apply(FakeRequest())
      val json: JsValue = contentAsJson(result)
      json mustBe Json.parse("""[{"chatId": 1,"subject": "Ok","lastAddress": "Ok","lastEmailDate": "Ok","contentPreview": "Ok"}]""")
    }
  }

  "ChatController#getChats" should {
    "return Json for drafts" in {
      val mockChatService = mock[ChatService]
      when(mockChatService.getChats(eqTo(Drafts), anyInt))
        .thenReturn(Future.successful(Seq(ChatPreviewDTO(1, "Ok", "Ok", "Ok", "Ok"))))

      val controller = new ChatController(cc, mockChatService)
      val result: Future[Result] = controller.getChats("drafts").apply(FakeRequest())
      val json: JsValue = contentAsJson(result)
      json mustBe Json.parse("""[{"chatId": 1,"subject": "Ok","lastAddress": "Ok","lastEmailDate": "Ok","contentPreview": "Ok"}]""")
    }
  }

  "ChatController#getChats" should {
    "return Not Found" in {
      val mockChatService = mock[ChatService]
      val controller = new ChatController(cc, mockChatService)
      val result: Future[Result] = controller.getChats(Random.alphanumeric.take(10).mkString).apply(FakeRequest())
      result.map(_ mustBe NotFound)
    }
  }
  //endregion

}
