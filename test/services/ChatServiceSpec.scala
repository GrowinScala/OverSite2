package services

import model.dtos._
import model.types.Mailbox.Inbox
import org.mockito.scalatest.AsyncIdiomaticMockito
import org.scalatest.{ AsyncWordSpec, MustMatchers }
import repositories.ChatsRepository
import repositories.dtos._
import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }

class ChatServiceSpec extends AsyncWordSpec with AsyncIdiomaticMockito with MustMatchers {

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  "ChatService#getChats" should {
    "map ChatPreview DTO" in {
      val mockChatsRep = mock[ChatsRepository]
      mockChatsRep.getChatsPreview(*, *)
        .returns(Future.successful(Seq(ChatPreview("00000000-0000-0000-0000-000000000000", "Ok", "Ok", "Ok", "Ok"))))

      val chatServiceImpl = new ChatService(mockChatsRep)
      val chatsPreviewDTO = chatServiceImpl.getChats(Inbox, "00000000-0000-0000-0000-000000000000")
      chatsPreviewDTO.map(_ mustBe Seq(ChatPreviewDTO("00000000-0000-0000-0000-000000000000", "Ok", "Ok", "Ok", "Ok")))
    }
  }

  "ChatService#getChat" should {
    "map Chat DTO" in {
      val mockChatsRep = mock[ChatsRepository]
      mockChatsRep.getChat(*, *)
        .returns(Future.successful(
          Some(
            Chat(
              "6c664490-eee9-4820-9eda-3110d794a998", "Subject", Set("address1", "address2"),
              Set(Overseers("address1", Set("address3"))),
              Seq(Email("f15967e6-532c-40a6-9335-064d884d4906", "address1", Set("address2"), Set(), Set(),
                "This is the body", "2019-07-19 10:00:00", 1, Set("65aeedbf-aedf-4b1e-b5d8-b348309a14e0")))))))

      val chatService = new ChatService(mockChatsRep)
      val chatDTO = chatService.getChat(chatId = "6c664490-eee9-4820-9eda-3110d794a998", userId = "685c9120-6616-47ab-b1a7-c5bd9b11c32b")
      val expectedServiceResponse =
        Some(
          ChatDTO(
            "6c664490-eee9-4820-9eda-3110d794a998", "Subject", Set("address1", "address2"),
            Set(OverseersDTO("address1", Set("address3"))),
            Seq(EmailDTO("f15967e6-532c-40a6-9335-064d884d4906", "address1", Set("address2"), Set(), Set(),
              "This is the body", "2019-07-19 10:00:00", sent = true, Set("65aeedbf-aedf-4b1e-b5d8-b348309a14e0")))))
      chatDTO.map(_ mustBe expectedServiceResponse)
    }
  }

  "ChatService#postChat" should {
    "return a CreateChatDTO equal to the input plus a new chatId and a new emailID" in {
      val createChatDTO =
        CreateChatDTO(None, Some("Subject"),
          UpsertEmailDTO(None, "beatriz@mail.com", Some(Set("joao@mail.com")), None, //no BCC field
            Some(Set("")), Some("This is the body"), Some("2019-07-26 15:00:00"), Some(false)))

      val expectedResponse = createChatDTO.copy(chatId = Some("newChatId"), email = createChatDTO.email.copy(emailId = Some("newEmailId")))

      val mockChatsRep = mock[ChatsRepository]
      mockChatsRep.postChat(*, *)
        .returns(
          Future.successful(expectedResponse))

      val chatService = new ChatService(mockChatsRep)
      val serviceResponse = chatService.postChat(createChatDTO, userId = "randomUserId")

      serviceResponse.map(_ mustBe expectedResponse)
    }
  }

  "ChatService#postEmail" should {
    "return a CreateChatDTO that contains the input emailDTO plus the chatId and a new emailID" in {
      val createEmailDTO =
        UpsertEmailDTO(None, "beatriz@mail.com", Some(Set("joao@mail.com")), None, //no BCC field
          Some(Set("")), Some("This is the body"), Some("2019-07-26 15:00:00"), Some(false))

      val expectedResponse =
        CreateChatDTO(Some("ChatId"), Some("Subject"), createEmailDTO.copy(emailId = Some("newEmailId")))

      val mockChatsRep = mock[ChatsRepository]
      mockChatsRep.postEmail(*, *, *)
        .returns(Future.successful(Some(expectedResponse)))

      val chatService = new ChatService(mockChatsRep)
      val serviceResponse = chatService.postEmail(createEmailDTO, "userId", "chatID")

      serviceResponse.map(_ mustBe Some(expectedResponse))
    }
  }

}
