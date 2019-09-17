package services

import model.dtos.PatchChatDTO.{ MoveToTrash, Restore }
import model.dtos._
import model.types.Mailbox.Inbox
import org.mockito.scalatest.AsyncIdiomaticMockito
import org.scalatest.{ AsyncWordSpec, MustMatchers, OptionValues }
import repositories.ChatsRepository
import repositories.dtos._

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }

import scala.concurrent.Future
import utils.TestGenerators._

class ChatServiceSpec extends AsyncWordSpec
  with AsyncIdiomaticMockito with MustMatchers with OptionValues {

  def getServiceAndRepMock: (ChatService, ChatsRepository) = {
    implicit val mockChatsRep: ChatsRepository = mock[ChatsRepository]
    val chatServiceImpl = new ChatService()
    (chatServiceImpl, mockChatsRep)
  }

  "ChatService#getChats" should {
    "map ChatPreview DTO" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      val testChatsPreviewDTO = genChatPreviewDTOSeq.sample.value
      val chatsPreview = ChatPreviewDTO.toSeqChatPreview(testChatsPreviewDTO)

      mockChatsRep.getChatsPreview(*, *)
        .returns(Future.successful(chatsPreview))

      val chatsPreviewDTO = chatService.getChats(Inbox, chatsPreview.head.chatId)
      chatsPreviewDTO.map(_ mustBe testChatsPreviewDTO)
    }
  }

  "ChatService#getChat" should {
    "map Chat DTO" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      val testchatDTO = genChatDTO.sample.value
      mockChatsRep.getChat(*, *)
        .returns(Future.successful(
          Some(ChatDTO.toChat(testchatDTO))))

      val chatDTO = chatService.getChat(genUUID.sample.value, genUUID.sample.value)
      val expectedServiceResponse =
        Some(testchatDTO)
      chatDTO.map(_ mustBe expectedServiceResponse)
    }
  }

  "ChatService#postChat" should {
    "return a CreateChatDTO equal to the input plus a new chatId and a new emailID" in {
      val randomCreateChatDTO = genCreateChatDTOption.sample.value

      val newCreateChatDTO = randomCreateChatDTO.copy(
        chatId = None,
        email = randomCreateChatDTO.email.copy(emailId = None))

      val expectedResponse = newCreateChatDTO.copy(
        chatId = Some(genUUID.sample.value),
        email = newCreateChatDTO.email.copy(emailId = Some(genUUID.sample.value)))

      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.postChat(*, *)
        .returns(
          Future.successful(expectedResponse))

      val serviceResponse = chatService.postChat(newCreateChatDTO, genUUID.sample.value)

      serviceResponse.map(_ mustBe expectedResponse)
    }
  }

  "ChatService#postEmail" should {
    "return a CreateChatDTO that contains the input emailDTO plus the chatId and a new emailID" in {
      val upsertEmailDTO = genUpsertEmailDTOption.sample.value.copy(emailId = None)

      val expectedResponse = genCreateChatDTOption.sample.value.copy(
        email = upsertEmailDTO.copy(emailId = Some(genUUID.sample.value)))

      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.postEmail(*, *, *)
        .returns(Future.successful(Some(expectedResponse)))

      val serviceResponse = chatService.postEmail(upsertEmailDTO, genUUID.sample.value, genUUID.sample.value)

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

      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.patchEmail(*, *, *, *)
        .returns(Future.successful(Some(returnedEmail)))

      val serviceResponse = chatService.patchEmail(upsertEmailDTO, "chatId", "emailId", "userId")

      serviceResponse.map(_ mustBe Some(expectedResponse))
    }
  }

  "ChatService#getEmail" should {
    "return a ChatDTO with the requested email" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock

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
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.deleteChat(*, *)
        .returns(Future.successful(true))

      val chatServiceImpl = new ChatService(mockChatsRep)
      val deleteChatService = chatServiceImpl.deleteChat("303c2b72-304e-4bac-84d7-385acb64a616", "148a3b1b-8326-466d-8c27-1bd09b8378f3")
      deleteChatService.map(_ mustBe true)
    }
    "return false if the ChatsRepository returns false" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
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
