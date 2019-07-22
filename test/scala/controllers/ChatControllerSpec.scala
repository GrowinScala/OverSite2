package scala.controllers

import controllers.ChatController
import org.scalatestplus.play._
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }
import scala.services.FakeChatService
import scala.util.Random

class ChatControllerSpec extends PlaySpec with Results {

  private lazy val appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
  private lazy val injector: Injector = appBuilder.injector()
  private val cc: ControllerComponents = Helpers.stubControllerComponents()
  private val chatService = injector.instanceOf[FakeChatService]
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  "ChatController#getChats" should {
    "return Json for inbox" in {
      val controller = new ChatController(cc, chatService)
      val result: Future[Result] = controller.getChats("inbox").apply(FakeRequest())
      val json: JsValue = contentAsJson(result)
      json mustBe Json.parse("""[{"chatId": 1,"subject": "Ok","lastAddress": "Ok","lastEmailDate": "Ok","contentPreview": "Ok"}]""")
    }
  }

  "ChatController#getChats" should {
    "return Json for sent" in {
      val controller = new ChatController(cc, chatService)
      val result: Future[Result] = controller.getChats("sent").apply(FakeRequest())
      val json: JsValue = contentAsJson(result)
      json mustBe Json.parse("""[{"chatId": 1,"subject": "Ok","lastAddress": "Ok","lastEmailDate": "Ok","contentPreview": "Ok"}]""")
    }
  }

  "ChatController#getChats" should {
    "return Json for trash" in {
      val controller = new ChatController(cc, chatService)
      val result: Future[Result] = controller.getChats("trash").apply(FakeRequest())
      val json: JsValue = contentAsJson(result)
      json mustBe Json.parse("""[{"chatId": 1,"subject": "Ok","lastAddress": "Ok","lastEmailDate": "Ok","contentPreview": "Ok"}]""")
    }
  }

  "ChatController#getChats" should {
    "return Json for drafts" in {
      val controller = new ChatController(cc, chatService)
      val result: Future[Result] = controller.getChats("drafts").apply(FakeRequest())
      val json: JsValue = contentAsJson(result)
      json mustBe Json.parse("""[{"chatId": 1,"subject": "Ok","lastAddress": "Ok","lastEmailDate": "Ok","contentPreview": "Ok"}]""")
    }
  }

  "ChatController#getChats" should {
    "return Not Found" in {
      val controller = new ChatController(cc, chatService)
      val result: Future[Result] = controller.getChats(Random.alphanumeric.take(10).mkString).apply(FakeRequest())
      result.map(_ mustBe NotFound)
    }
  }

  "ChatController#getChat" should {
    "return Json for ChatDTO" in {
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
      val controller = new ChatController(cc, chatService)
      val result: Future[Result] = controller.getChat(1).apply(FakeRequest())
      result.map(_ mustBe NotFound)
    }
  }
}
