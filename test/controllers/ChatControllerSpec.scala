package controllers

import model.dtos._
import model.types.Mailbox._
import org.scalatestplus.play._
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import services.ChatService
import org.mockito.scalatest.IdiomaticMockito
import utils.Jsons._

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }
import scala.util.Random

class ChatControllerSpec extends PlaySpec with Results with IdiomaticMockito {

  private val LOCALHOST = "localhost:9000"
  private lazy val appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
  private lazy val injector: Injector = appBuilder.injector()
  private val cc: ControllerComponents = Helpers.stubControllerComponents()
  private implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  "ChatController#getChats" should {
    "return Json for inbox" in {
      val mockChatService = mock[ChatService]
      mockChatService.getChats(Inbox, *)
        .returns(Future.successful(Seq(
          ChatPreviewDTO("00000000-0000-0000-0000-000000000000", "Ok", "Ok", "Ok", "Ok"))))

      val fakeAuthenticatedUserAction = new FakeAuthenticatedUserAction

      val controller = new ChatController(cc, mockChatService, fakeAuthenticatedUserAction)
      val result: Future[Result] = controller.getChats(Inbox).apply(FakeRequest())
      val json: JsValue = contentAsJson(result)
      json mustBe Json.parse(
        """[{"chatId": "00000000-0000-0000-0000-000000000000","subject": "Ok","lastAddress": "Ok","lastEmailDate": "Ok","contentPreview": "Ok"}]""")
    }

    "return Json for sent" in {
      val mockChatService = mock[ChatService]
      mockChatService.getChats(Sent, *)
        .returns(Future.successful(Seq(ChatPreviewDTO("00000000-0000-0000-0000-000000000000", "Ok", "Ok", "Ok", "Ok"))))

      val fakeAuthenticatedUserAction = new FakeAuthenticatedUserAction

      val controller = new ChatController(cc, mockChatService, fakeAuthenticatedUserAction)
      val result: Future[Result] = controller.getChats(Sent).apply(FakeRequest())
      val json: JsValue = contentAsJson(result)
      json mustBe Json.parse("""[{"chatId": "00000000-0000-0000-0000-000000000000","subject": "Ok","lastAddress": "Ok","lastEmailDate": "Ok","contentPreview": "Ok"}]""")
    }

    "return Json for trash" in {
      val mockChatService = mock[ChatService]
      mockChatService.getChats(Trash, *)
        .returns(Future.successful(Seq(ChatPreviewDTO("00000000-0000-0000-0000-000000000000", "Ok", "Ok", "Ok", "Ok"))))

      val fakeAuthenticatedUserAction = new FakeAuthenticatedUserAction

      val controller = new ChatController(cc, mockChatService, fakeAuthenticatedUserAction)
      val result: Future[Result] = controller.getChats(Trash).apply(FakeRequest())
      val json: JsValue = contentAsJson(result)
      json mustBe Json.parse("""[{"chatId": "00000000-0000-0000-0000-000000000000","subject": "Ok","lastAddress": "Ok","lastEmailDate": "Ok","contentPreview": "Ok"}]""")
    }

    "return Json for drafts" in {
      val mockChatService = mock[ChatService]
      mockChatService.getChats(Drafts, *)
        .returns(Future.successful(Seq(ChatPreviewDTO("00000000-0000-0000-0000-000000000000", "Ok", "Ok", "Ok", "Ok"))))

      val fakeAuthenticatedUserAction = new FakeAuthenticatedUserAction

      val controller = new ChatController(cc, mockChatService, fakeAuthenticatedUserAction)
      val result: Future[Result] = controller.getChats(Drafts).apply(FakeRequest())
      val json: JsValue = contentAsJson(result)
      json mustBe Json.parse("""[{"chatId": "00000000-0000-0000-0000-000000000000","subject": "Ok","lastAddress": "Ok","lastEmailDate": "Ok","contentPreview": "Ok"}]""")
    }
    
  }

  "ChatController#getChat" should {
    "return Json for ChatDTO" in {
      val dto = ChatDTO(
        "6c664490-eee9-4820-9eda-3110d794a998", "Subject", Set("address1", "address2"), Set(OverseersDTO("address1", Set("address3"))),
        Seq(EmailDTO("f15967e6-532c-40a6-9335-064d884d4906", "address1", Set("address2"), Set(), Set(), "This is the body", "2019-07-19 10:00:00", sent = true,
          Set("65aeedbf-aedf-4b1e-b5d8-b348309a14e0"))))

      val mockChatService = mock[ChatService]
      mockChatService.getChat(*, *)
        .returns(Future.successful(
          Some(
            ChatDTO(
              "6c664490-eee9-4820-9eda-3110d794a998", "Subject", Set("address1", "address2"), Set(OverseersDTO("address1", Set("address3"))),
              Seq(EmailDTO("f15967e6-532c-40a6-9335-064d884d4906", "address1", Set("address2"), Set(), Set(), "This is the body", "2019-07-19 10:00:00", sent = true,
                Set("65aeedbf-aedf-4b1e-b5d8-b348309a14e0")))))))

      val fakeAuthenticatedUserAction = new FakeAuthenticatedUserAction

      val controller = new ChatController(cc, mockChatService, fakeAuthenticatedUserAction)
      val result: Future[Result] = controller.getChat("6c664490-eee9-4820-9eda-3110d794a998").apply(FakeRequest())
      val expectedResult = //Json.toJson(dto)

        """{"chatId": "6c664490-eee9-4820-9eda-3110d794a998","subject": "Subject","addresses": ["address1", "address2"],
          |"overseers":[{"user": "address1","overseers": ["address3"]}],"emails": [{"emailId": "f15967e6-532c-40a6-9335-064d884d4906","from":"address1","to":
          |["address2"],"bcc":[],"cc": [],"body": "This is the body","date":"2019-07-19 10:00:00","sent":
          |true,"attachments": ["65aeedbf-aedf-4b1e-b5d8-b348309a14e0"]}]}""".stripMargin

      result.map(_ mustBe Ok(expectedResult))
    }

    "return NotFound" in {
      val mockChatService = mock[ChatService]
      mockChatService.getChat(*, *)
        .returns(Future.successful(None))

      val fakeAuthenticatedUserAction = new FakeAuthenticatedUserAction
      val controller = new ChatController(cc, mockChatService, fakeAuthenticatedUserAction)
      val result: Future[Result] = controller.getChat("6c664490-eee9-4820-9eda-3110d794a998").apply(FakeRequest())
      result.map(_ mustBe NotFound)
    }

  }

  "ChatController#postChat" should {
    "return Json with the chat received plus a new chatId and a new emailId" in {

      val mockChatService: ChatService = mock[ChatService]

      val createChatDTO =
        CreateChatDTO(Some("newChatId"), Some("Subject"),
          UpsertEmailDTO(Some("newEmailId"), "beatriz@mail.com", Some(Set("joao@mail.com")), None, //no BCC field
            Some(Set()), Some("This is the body"), Some("2019-07-26 15:00:00")))

      mockChatService.postChat(*, *)
        .returns(Future.successful(createChatDTO))

      val chatJsonRequest = Json.parse(
        """{
          |    "subject": "Subject",
          |    "email": {
          |        "from": "beatriz@mail.com",
          |        "to": ["joao@mail.com"],
          |        "cc": [],
          |        "body": "This is the body"
          |   }
          |}""".stripMargin)

      val chatJsonResponse = Json.parse(
        """{
          |    "chatId": "newChatId",
          |    "subject": "Subject",
          |    "email": {
          |        "emailId": "newEmailId",
          |        "from": "beatriz@mail.com",
          |        "to": ["joao@mail.com"],
          |        "cc": [],
          |        "body": "This is the body",
          |        "date": "2019-07-26 15:00:00"
          |   }
          |}""".stripMargin)

      val fakeAuthenticatedUserAction = new FakeAuthenticatedUserAction
      val controller = new ChatController(cc, mockChatService, fakeAuthenticatedUserAction)

      val request = FakeRequest(POST, "/chats")
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(chatJsonRequest)

      val result: Future[Result] = controller.postChat.apply(request)

      val json = contentAsJson(result)

      status(result) mustBe OK
      json mustBe chatJsonResponse
    }

    "return 400 Bad Request if any of the email addresses is not a valid address" in {
      val mockChatService = mock[ChatService]

      val chatWithInvalidFromAddress = Json.parse(
        """{
          |    "email": {
          |        "from": "beatrizmailcom",
          |        "to": ["joao@mail.com"],
          |        "body": "This is the body"
          |   }
          |}""".stripMargin)

      val fakeAuthenticatedUserAction = new FakeAuthenticatedUserAction
      val controller = new ChatController(cc, mockChatService, fakeAuthenticatedUserAction)

      val request = FakeRequest(POST, "/chats")
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(chatWithInvalidFromAddress)

      val result: Future[Result] = controller.postChat.apply(request)

      status(result) mustBe BAD_REQUEST
    }
  }

  "ChatController#postEmail" should {
    "return Json with the email received plus the chat data and a new emailId" in {

      val mockChatService: ChatService = mock[ChatService]

      val createChatDTO =
        CreateChatDTO(Some("ChatId"), Some("Subject"),
          UpsertEmailDTO(Some("newEmailId"), "beatriz@mail.com", Some(Set("joao@mail.com")), None, //no BCC field
            Some(Set()), Some("This is the body"), Some("2019-07-26 15:00:00")))

      mockChatService.postEmail(*, *, *)
        .returns(Future.successful(Some(createChatDTO)))

      val emailJsonRequest = Json.parse(
        """{
          |        "from": "beatriz@mail.com",
          |        "to": ["joao@mail.com"],
          |        "cc": [],
          |        "body": "This is the body"
          |}""".stripMargin)

      val chatJsonResponse = Json.parse(
        """{
          |    "chatId":  "ChatId",
          |    "subject": "Subject",
          |    "email": {
          |        "emailId": "newEmailId",
          |        "from": "beatriz@mail.com",
          |        "to": ["joao@mail.com"],
          |        "cc": [],
          |        "body": "This is the body",
          |        "date": "2019-07-26 15:00:00"
          |   }
          |}""".stripMargin)

      val fakeAuthenticatedUserAction = new FakeAuthenticatedUserAction
      val controller = new ChatController(cc, mockChatService, fakeAuthenticatedUserAction)

      val request = FakeRequest()
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(emailJsonRequest)

      val result: Future[Result] = controller.postEmail("").apply(request)

      val json = contentAsJson(result)

      status(result) mustBe OK
      json mustBe chatJsonResponse
    }

    "return 400 Bad Request if any of the email addresses is not a valid address" in {
      val mockChatService = mock[ChatService]

      val emailWithInvalidFromAddress = Json.parse(
        """{
          |        "from": "beatrizmailcom",
          |        "to": ["joao@mail.com"],
          |        "body": "This is the body"
          |}""".stripMargin)

      val fakeAuthenticatedUserAction = new FakeAuthenticatedUserAction
      val controller = new ChatController(cc, mockChatService, fakeAuthenticatedUserAction)

      val request = FakeRequest()
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(emailWithInvalidFromAddress)

      val result: Future[Result] = controller.postChat.apply(request)

      status(result) mustBe BAD_REQUEST
    }

    "return Notfound if the service return None" in {

      val mockChatService: ChatService = mock[ChatService]

      mockChatService.postEmail(*, *, *)
        .returns(Future.successful(None))

      val emailJsonRequest = Json.parse(
        """{
          |        "from": "beatriz@mail.com",
          |        "to": ["joao@mail.com"],
          |        "cc": [],
          |        "body": "This is the body"
          |}""".stripMargin)

      val fakeAuthenticatedUserAction = new FakeAuthenticatedUserAction
      val controller = new ChatController(cc, mockChatService, fakeAuthenticatedUserAction)

      val request = FakeRequest()
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(emailJsonRequest)

      val result: Future[Result] = controller.postEmail("").apply(request)

      val json = contentAsJson(result)

      status(result) mustBe NOT_FOUND
      json mustBe chatNotFound
    }

  }
}