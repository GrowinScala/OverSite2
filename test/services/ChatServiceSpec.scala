package services

import model.dtos.PatchChatDTO.{ ChangeSubject, MoveToTrash, Restore }
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
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.patchChat(MoveToTrash, *, *)
        .returns(Future.successful(Some(MoveToTrash)))

      val moveChatToTrashService = chatService
        .patchChat(MoveToTrash, genUUID.sample.value, genUUID.sample.value)
      moveChatToTrashService.map(_ mustBe Some(MoveToTrash))
    }
    "return some Restore DTO if the ChatsRepository returns some Restore DTO" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.patchChat(Restore, *, *)
        .returns(Future.successful(Some(Restore)))

      val moveChatToTrashService = chatService
        .patchChat(Restore, genUUID.sample.value, genUUID.sample.value)
      moveChatToTrashService.map(_ mustBe Some(Restore))
    }
    "return some ChangeSubject DTO if the ChatsRepository returns some ChangeSubject DTO" in {
      val newSubject = "New Subject"
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.patchChat(ChangeSubject(newSubject), *, *)
        .returns(Future.successful(Some(ChangeSubject(newSubject))))

      val moveChatToTrashService = chatService
        .patchChat(ChangeSubject(newSubject), genUUID.sample.value, genUUID.sample.value)
      moveChatToTrashService.map(_ mustBe Some(ChangeSubject(newSubject)))
    }
    "return None if the ChatsRepository returns None" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.patchChat(*, *, *)
        .returns(Future.successful(None))

      val moveChatToTrashService = chatService
        .patchChat(MoveToTrash, genUUID.sample.value, genUUID.sample.value)
      moveChatToTrashService.map(_ mustBe None)
    }
  }

  "ChatService#patchEmail" should {
    "return an EmailDTO that contains the email with the updated/patched fields" in {
      val returnedEmail = genEmail.sample.value

      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.patchEmail(*, *, *, *)
        .returns(Future.successful(Some(returnedEmail)))

      val serviceResponse = chatService.patchEmail(
        genUpsertEmailDTOption.sample.value,
        genUUID.sample.value, genUUID.sample.value, genUUID.sample.value)

      serviceResponse.map(_ mustBe chatService.toEmailDTO(Some(returnedEmail)))
    }
  }

  "ChatService#getEmail" should {
    "return a ChatDTO with the requested email" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock

      val repositoryChatResponse = genChat.sample.value

      mockChatsRep.getEmail(*, *, *)
        .returns(Future.successful(Some(repositoryChatResponse)))

      val expectedServiceResponse = chatService.toChatDTO(Some(repositoryChatResponse))

      chatService.getEmail(genUUID.sample.value, genUUID.sample.value, genUUID.sample.value).map(
        serviceResponse => serviceResponse.value mustBe expectedServiceResponse.value)
    }
  }

  "ChatService#deleteChat" should {
    "return true if the ChatsRepository returns true" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.deleteChat(*, *)
        .returns(Future.successful(true))

      val deleteChatService = chatService.deleteChat(genUUID.sample.value, genUUID.sample.value)
      deleteChatService.map(_ mustBe true)
    }
    "return false if the ChatsRepository returns false" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.deleteChat(*, *)
        .returns(Future.successful(false))

      val deleteChatService = chatService.deleteChat(genUUID.sample.value, genUUID.sample.value)
      deleteChatService.map(_ mustBe false)
    }
  }

  "ChatService#deleteDraft" should {
    "return true if the ChatsRepository returns true" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.deleteDraft(*, *, *)
        .returns(Future.successful(true))

      val deleteDraftService = chatService.deleteDraft(
        genUUID.sample.value, genUUID.sample.value, genUUID.sample.value)
      deleteDraftService.map(_ mustBe true)
    }
    "return false if the ChatsRepository returns false" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.deleteDraft(*, *, *)
        .returns(Future.successful(false))

      val deleteDraftService = chatService.deleteDraft(
        genUUID.sample.value, genUUID.sample.value, genUUID.sample.value)
      deleteDraftService.map(_ mustBe false)
    }
  }

}
