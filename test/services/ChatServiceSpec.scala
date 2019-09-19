package services

import model.dtos.PatchChatDTO.{ ChangeSubject, MoveToTrash, Restore }
import model.dtos._
import model.types.Mailbox.Inbox
import org.mockito.scalatest.AsyncIdiomaticMockito
import org.scalatest.{ AsyncWordSpec, MustMatchers, OptionValues }
import repositories.ChatsRepository
import repositories.dtos._

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }

class ChatServiceSpec extends AsyncWordSpec with OptionValues with AsyncIdiomaticMockito with MustMatchers {

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
          UpsertEmailDTO(None, Some("beatriz@mail.com"), Some(Set("joao@mail.com")), None, //no BCC field
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
        UpsertEmailDTO(None, Some("beatriz@mail.com"), Some(Set("joao@mail.com")), None, //no BCC field
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

  "ChatService#patchChat" should {
    "return some MoveToTrash DTO if the ChatsRepository returns some MoveToTrash DTO" in {
      val mockChatsRep = mock[ChatsRepository]
      mockChatsRep.patchChat(MoveToTrash, *, *)
        .returns(Future.successful(Some(MoveToTrash)))

      val chatServiceImpl = new ChatService(mockChatsRep)
      val moveChatToTrashService = chatServiceImpl
        .patchChat(MoveToTrash, "303c2b72-304e-4bac-84d7-385acb64a616", "148a3b1b-8326-466d-8c27-1bd09b8378f3")
      moveChatToTrashService.map(_ mustBe Some(MoveToTrash))
    }
    "return some Restore DTO if the ChatsRepository returns some Restore DTO" in {
      val mockChatsRep = mock[ChatsRepository]
      mockChatsRep.patchChat(Restore, *, *)
        .returns(Future.successful(Some(Restore)))

      val chatServiceImpl = new ChatService(mockChatsRep)
      val moveChatToTrashService = chatServiceImpl
        .patchChat(Restore, "303c2b72-304e-4bac-84d7-385acb64a616", "148a3b1b-8326-466d-8c27-1bd09b8378f3")
      moveChatToTrashService.map(_ mustBe Some(Restore))
    }
    "return some ChangeSubject(subject) DTO if the ChatsRepository returns some ChangeSubject(subject) DTO" in {
      val mockChatsRep = mock[ChatsRepository]
      mockChatsRep.patchChat(ChangeSubject("New Subject"), *, *)
        .returns(Future.successful(Some(ChangeSubject("New Subject"))))

      val chatServiceImpl = new ChatService(mockChatsRep)
      val moveChatToTrashService = chatServiceImpl
        .patchChat(ChangeSubject("New Subject"), "303c2b72-304e-4bac-84d7-385acb64a616", "148a3b1b-8326-466d-8c27-1bd09b8378f3")
      moveChatToTrashService.map(_ mustBe Some(ChangeSubject("New Subject")))
    }
    "return None if the ChatsRepository returns None" in {
      val mockChatsRep = mock[ChatsRepository]
      mockChatsRep.patchChat(*, *, *)
        .returns(Future.successful(None))

      val chatServiceImpl = new ChatService(mockChatsRep)
      val moveChatToTrashService = chatServiceImpl
        .patchChat(MoveToTrash, "00000000-0000-0000-0000-000000000000", "148a3b1b-8326-466d-8c27-1bd09b8378f3")
      moveChatToTrashService.map(_ mustBe None)
    }
  }

  "ChatService#patchEmail" should {
    "return an EmailDTO that contains the email with the updated/patched fields" in {
      val upsertEmailDTO =
        UpsertEmailDTO(None, None, Some(Set("joao@mail.com")), None, Some(Set("")),
          Some("This is the patched body"), None, Some(false))

      val returnedEmail = Email("emailId", "beatriz@mail.com", Set("joao@mail.com"), Set(), Set(), "This is the patched body",
        "2019-09-02 12:00:00", 0, Set())

      val expectedResponse =
        EmailDTO("emailId", "beatriz@mail.com", Set("joao@mail.com"), Set(), Set(), "This is the patched body",
          "2019-09-02 12:00:00", sent = false, Set())

      val mockChatsRep = mock[ChatsRepository]
      mockChatsRep.patchEmail(*, *, *, *)
        .returns(Future.successful(Some(returnedEmail)))

      val chatService = new ChatService(mockChatsRep)
      val serviceResponse = chatService.patchEmail(upsertEmailDTO, "chatId", "emailId", "userId")

      serviceResponse.map(_ mustBe Some(expectedResponse))
    }
  }

  "ChatService#getEmail" should {
    "return a ChatDTO with the requested email" in {
      val mockChatsRep = mock[ChatsRepository]

      val chatId = "6c664490-eee9-4820-9eda-3110d794a998"
      val emailId = "f15967e6-532c-40a6-9335-064d884d4906"
      val userId = "685c9120-6616-47ab-b1a7-c5bd9b11c32b"

      val repositoryChatResponse = Chat(
        chatId, "Subject", Set("address1", "address2"),
        Set(Overseers("address1", Set("address3"))),
        Seq(Email(emailId, "address1", Set("address2"), Set(), Set(),
          "This is the body", "2019-07-19 10:00:00", 1, Set("65aeedbf-aedf-4b1e-b5d8-b348309a14e0"))))

      mockChatsRep.getEmail(*, *, *)
        .returns(Future.successful(Some(repositoryChatResponse)))

      val chatService = new ChatService(mockChatsRep)

      val expectedServiceResponse = ChatDTO(chatId, "Subject", Set("address1", "address2"),
        Set(OverseersDTO("address1", Set("address3"))),
        Seq(EmailDTO(emailId, "address1", Set("address2"), Set(), Set(),
          "This is the body", "2019-07-19 10:00:00", sent = true, Set("65aeedbf-aedf-4b1e-b5d8-b348309a14e0"))))

      chatService.getEmail(chatId, emailId, userId).map(
        serviceResponse => serviceResponse.value mustBe expectedServiceResponse)
    }
  }

  "ChatService#deleteChat" should {
    "return true if the ChatsRepository returns true" in {
      val mockChatsRep = mock[ChatsRepository]
      mockChatsRep.deleteChat(*, *)
        .returns(Future.successful(true))

      val chatServiceImpl = new ChatService(mockChatsRep)
      val deleteChatService = chatServiceImpl.deleteChat("303c2b72-304e-4bac-84d7-385acb64a616", "148a3b1b-8326-466d-8c27-1bd09b8378f3")
      deleteChatService.map(_ mustBe true)
    }
    "return false if the ChatsRepository returns false" in {
      val mockChatsRep = mock[ChatsRepository]
      mockChatsRep.deleteChat(*, *)
        .returns(Future.successful(false))

      val chatServiceImpl = new ChatService(mockChatsRep)
      val deleteChatService = chatServiceImpl.deleteChat("303c2b72-304e-4bac-84d7-385acb64a616", "148a3b1b-8326-466d-8c27-1bd09b8378f3")
      deleteChatService.map(_ mustBe false)
    }
  }

  "ChatService#deleteDraft" should {
    "return true if the ChatsRepository returns true" in {
      val mockChatsRep = mock[ChatsRepository]
      mockChatsRep.deleteDraft(*, *, *)
        .returns(Future.successful(true))

      val chatServiceImpl = new ChatService(mockChatsRep)
      val deleteDraftService = chatServiceImpl.deleteDraft(
        "303c2b72-304e-4bac-84d7-385acb64a616",
        "f203c270-5f37-4437-956a-3cf478f5f28f", "148a3b1b-8326-466d-8c27-1bd09b8378f3")
      deleteDraftService.map(_ mustBe true)
    }
    "return false if the ChatsRepository returns false" in {
      val mockChatsRep = mock[ChatsRepository]
      mockChatsRep.deleteDraft(*, *, *)
        .returns(Future.successful(false))

      val chatServiceImpl = new ChatService(mockChatsRep)
      val deleteDraftService = chatServiceImpl.deleteDraft(
        "303c2b72-304e-4bac-84d7-385acb64a616",
        "825ee397-f36e-4023-951e-89d6e43a8e7d", "148a3b1b-8326-466d-8c27-1bd09b8378f3")
      deleteDraftService.map(_ mustBe false)
    }
  }

}
