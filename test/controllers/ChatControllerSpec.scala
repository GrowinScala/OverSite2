package controllers

import model.dtos._
import model.types.Mailbox._
import org.scalatestplus.play._
import play.api.libs.json._
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import org.mockito.scalatest.IdiomaticMockito
import services.ChatService

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }
import scala.util.Random

class ChatControllerSpec extends PlaySpec with Results with IdiomaticMockito {

  private val LOCALHOST = "localhost:9000"

  private val cc: ControllerComponents = Helpers.stubControllerComponents()
  private implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  "ChatController#getChats" should {
    "return Json for inbox" in {
      val mockChatService = mock[ChatService]
      mockChatService.getChats(Inbox, *)
        .returns(Future.successful(Seq(
          ChatPreviewDTO("00000000-0000-0000-0000-000000000000", "Ok", "Ok", "Ok", "Ok"))))

      val controller = new ChatController(cc, mockChatService)
      val result: Future[Result] = controller.getChats("inbox").apply(FakeRequest())
      val json: JsValue = contentAsJson(result)
      json mustBe Json.parse(
        """[{"chatId": "00000000-0000-0000-0000-000000000000","subject": "Ok","lastAddress": "Ok","lastEmailDate": "Ok","contentPreview": "Ok"}]""")
    }

    "return Json for sent" in {
      val mockChatService = mock[ChatService]
      mockChatService.getChats(Sent, *)
        .returns(Future.successful(Seq(ChatPreviewDTO("00000000-0000-0000-0000-000000000000", "Ok", "Ok", "Ok", "Ok"))))

      val controller = new ChatController(cc, mockChatService)
      val result: Future[Result] = controller.getChats("sent").apply(FakeRequest())
      val json: JsValue = contentAsJson(result)
      json mustBe Json.parse("""[{"chatId": "00000000-0000-0000-0000-000000000000","subject": "Ok","lastAddress": "Ok","lastEmailDate": "Ok","contentPreview": "Ok"}]""")
    }

    "return Json for trash" in {
      val mockChatService = mock[ChatService]
      mockChatService.getChats(Trash, *)
        .returns(Future.successful(Seq(ChatPreviewDTO("00000000-0000-0000-0000-000000000000", "Ok", "Ok", "Ok", "Ok"))))

      val controller = new ChatController(cc, mockChatService)
      val result: Future[Result] = controller.getChats("trash").apply(FakeRequest())
      val json: JsValue = contentAsJson(result)
      json mustBe Json.parse("""[{"chatId": "00000000-0000-0000-0000-000000000000","subject": "Ok","lastAddress": "Ok","lastEmailDate": "Ok","contentPreview": "Ok"}]""")
    }

    "return Json for drafts" in {
      val mockChatService = mock[ChatService]
      mockChatService.getChats(Drafts, *)
        .returns(Future.successful(Seq(ChatPreviewDTO("00000000-0000-0000-0000-000000000000", "Ok", "Ok", "Ok", "Ok"))))

      val controller = new ChatController(cc, mockChatService)
      val result: Future[Result] = controller.getChats("drafts").apply(FakeRequest())
      val json: JsValue = contentAsJson(result)
      json mustBe Json.parse("""[{"chatId": "00000000-0000-0000-0000-000000000000","subject": "Ok","lastAddress": "Ok","lastEmailDate": "Ok","contentPreview": "Ok"}]""")
    }

    "return Not Found" in {
      val mockChatService = mock[ChatService]
      val controller = new ChatController(cc, mockChatService)
      val result: Future[Result] = controller.getChats(Random.alphanumeric.take(10).mkString).apply(FakeRequest())
      result.map(_ mustBe NotFound)
    }
  }

  "ChatController#getChat" should {
    "return Json for ChatDTO" in {
      val dto = ChatDTO(
        "6c664490-eee9-4820-9eda-3110d794a998", "Subject", Set("address1", "address2"), Set(OverseersDTO("address1", Set("address3"))),
        Seq(EmailDTO("f15967e6-532c-40a6-9335-064d884d4906", "address1", Set("address2"), Set(), Set(), "This is the body", "2019-07-19 10:00:00", true,
          Set("65aeedbf-aedf-4b1e-b5d8-b348309a14e0"))))

      val mockChatService = mock[ChatService]
      mockChatService.getChat(*, *)
        .returns(Future.successful(
          Some(
            ChatDTO(
              "6c664490-eee9-4820-9eda-3110d794a998", "Subject", Set("address1", "address2"), Set(OverseersDTO("address1", Set("address3"))),
              Seq(EmailDTO("f15967e6-532c-40a6-9335-064d884d4906", "address1", Set("address2"), Set(), Set(), "This is the body", "2019-07-19 10:00:00", true,
                Set("65aeedbf-aedf-4b1e-b5d8-b348309a14e0")))))))

      val controller = new ChatController(cc, mockChatService)
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

      val controller = new ChatController(cc, mockChatService)
      val result: Future[Result] = controller.getChat("6c664490-eee9-4820-9eda-3110d794a998").apply(FakeRequest())
      result.map(_ mustBe NotFound)
    }

  }

  "ChatController#postChat" should {
    "return Json with the chat received plus a new chatId and a new emailId" in {

      val mockChatService: ChatService = mock[ChatService]

      val createChatDTO =
        CreateChatDTO(Some("newChatId"), "Subject",
          CreateEmailDTO(Some("newEmailId"), "beatriz@mail.com", Some(Set("joao@mail.com")), None, //no BCC field
            Some(Set("")), Some("This is the body"), "2019-07-26 15:00:00"))

      mockChatService.postChat(*, *)
        .returns(Future.successful(Some(createChatDTO)))

      val chatJsonRequest = Json.parse(
        """{
          |    "subject": "Subject",
          |    "email": {
          |        "from": "beatriz@mail.com",
          |        "to": ["joao@mail.com"],
          |        "cc": [""],
          |        "body": "This is the body",
          |        "date": "2019-07-26 15:00:00"
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
          |        "cc": [""],
          |        "body": "This is the body",
          |        "date": "2019-07-26 15:00:00"
          |   }
          |}""".stripMargin)

      val controller = new ChatController(cc, mockChatService)

      val request = FakeRequest(POST, "/chats")
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(chatJsonRequest)

      val result: Future[Result] = controller.postChat.apply(request)

      val json = contentAsJson(result)

      status(result) mustBe OK
      json mustBe chatJsonResponse
    }

    "return 400 Bad Request if any of the non-optional fields is missing" in {

      val mockChatService = mock[ChatService]

      val chatWithMissingSubject = Json.parse(
        """{
          |    "email": {
          |        "from": "beatriz@mail.com",
          |        "to": ["joao@mail.com"],
          |        "cc": [""],
          |        "body": "This is the body",
          |        "date": "2019-07-26 15:00:00"
          |   }
          |}""".stripMargin)

      val controller = new ChatController(cc, mockChatService)

      val request = FakeRequest(POST, "/chats")
        .withHeaders(HOST -> LOCALHOST, CONTENT_TYPE -> "application/json")
        .withBody(chatWithMissingSubject)

      val result: Future[Result] = controller.postChat.apply(request)

      status(result) mustBe BAD_REQUEST
    }
  }
}
