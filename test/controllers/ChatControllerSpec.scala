package controllers

import model.dtos.{ ChatDTO, ChatPreviewDTO, EmailDTO, OverseersDTO }
import model.types.Mailbox._
import org.scalatest.mockito.MockitoSugar._
import org.scalatestplus.play._
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import services.{ ChatService, FakeChatService }
import org.mockito.Mockito.when
import org.mockito.ArgumentMatchersSugar._
import repositories.dtos.{ Chat, Email, Overseers }

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

  "ChatController#getChat" should {
    "return Json for ChatDTO" in {
      val mockChatService = mock[ChatService]
      when(mockChatService.getChat(anyInt, anyInt))
        .thenReturn(Future.successful(
          Some(
            ChatDTO(
              1, "Subject", Seq("address1", "address2"), Seq(OverseersDTO("address1", Seq("address3"))),
              Seq(EmailDTO(1, "address1", Seq("address2"), Seq(), Seq(), "This is the body", "2019-07-19 10:00:00", true, Seq(1)))))))
      val controller = new ChatController(cc, chatService)
      val result: Future[Result] = controller.getChat(1).apply(FakeRequest())
      val expectedResult =
        """{"chatId": 1,"subject": "Subject","addresses": ["address1", "address2"],
          |"overseers":[{"user": "address1","overseers": ["address3"]}],"emails": [{"emailId": 1,"from":"address1","to":
          |["address2"],"bcc":[],"cc": [],"body": "This is the body","date":"2019-07-19 10:00:00","sent":
          |true,"attachments": [1]}]}""".stripMargin
      result.map(_ mustBe Ok(expectedResult))
    }
  }

  "ChatController#getChat" should {
    "return NotFound" in {
      val mockChatService = mock[ChatService]
      when(mockChatService.getChat(anyInt, anyInt))
        .thenReturn(Future.successful(None))
      val controller = new ChatController(cc, chatService)
      val result: Future[Result] = controller.getChat(1).apply(FakeRequest())
      result.map(_ mustBe NotFound)
    }
  }
}
