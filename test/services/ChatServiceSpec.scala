package services

import model.dtos._
import model.dtos.PostOverseerDTO._
import model.types.Mailbox.Inbox
import org.mockito.scalatest.AsyncIdiomaticMockito
import org.scalacheck.Gen
import org.scalatest.{ AsyncWordSpec, MustMatchers, OptionValues }
import repositories.ChatsRepository
import repositories.dtos.PatchChat
import ChatPreviewDTO._
import Gen._
import model.types._

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
    "map the repositorie's result" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      val optTestChatsPreviewDTO = Gen.option(genChatPreviewDTOSeq).sample.value
      val optChatsPreview = optTestChatsPreviewDTO.map(toSeqChatPreview)
      val totalCount = choose(1, 10).sample.value
      val lastPage = choose(1, 10).sample.value

      mockChatsRep.getChatsPreview(*, *, *, *)
        .returns(Future.successful(optChatsPreview.map((_, totalCount, lastPage))))

      val chatsPreviewDTO = chatService.getChats(genMailbox.sample.value, genPage.sample.value,
        genPerPage.sample.value, genUUID.sample.value)
      chatsPreviewDTO.map(_ mustBe optTestChatsPreviewDTO.map((_, totalCount, Page(lastPage))))
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
      val createChatDTO = genCreateChatDTOption.sample.value

      val expectedRepoResponse = CreateChatDTO.toCreateChat(createChatDTO)

      val expectedServiceResponse = CreateChatDTO.toCreateChatDTO(expectedRepoResponse)

      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.postChat(*, *)
        .returns(
          Future.successful(Some(expectedRepoResponse)))

      val serviceResponse = chatService.postChat(createChatDTO, genUUID.sample.value)

      serviceResponse.map(_ mustBe Some(expectedServiceResponse))
    }

  }

  "ChatService#postEmail" should {
    "return a CreateChatDTO that contains the input emailDTO plus the chatId and a new emailID" in {
      val upsertEmailDTO = genUpsertEmailDTOption.sample.value

      val expectedResponse = CreateChatDTO.toCreateChat(genCreateChatDTOption.sample.value)

      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.postEmail(*, *, *)
        .returns(Future.successful(Some(expectedResponse)))

      val serviceResponse = chatService.postEmail(upsertEmailDTO, genUUID.sample.value, genUUID.sample.value)

      serviceResponse.map(_ mustBe Some(CreateChatDTO.toCreateChatDTO(expectedResponse)))
    }
  }

  "ChatService#patchChat" should {
    "return some MoveToTrash DTO if the ChatsRepository returns some MoveToTrash DTO" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.patchChat(PatchChat.MoveToTrash, *, *)
        .returns(Future.successful(Some(PatchChat.MoveToTrash)))

      val moveChatToTrashService = chatService
        .patchChat(PatchChatDTO.MoveToTrash, genUUID.sample.value, genUUID.sample.value)
      moveChatToTrashService.map(_ mustBe Some(PatchChatDTO.MoveToTrash))
    }
    "return some Restore DTO if the ChatsRepository returns some Restore DTO" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.patchChat(PatchChat.Restore, *, *)
        .returns(Future.successful(Some(PatchChat.Restore)))

      val moveChatToTrashService = chatService
        .patchChat(PatchChatDTO.Restore, genUUID.sample.value, genUUID.sample.value)
      moveChatToTrashService.map(_ mustBe Some(PatchChatDTO.Restore))
    }
    "return some ChangeSubject DTO if the ChatsRepository returns some ChangeSubject DTO" in {
      val newSubject = "New Subject"
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.patchChat(PatchChat.ChangeSubject(newSubject), *, *)
        .returns(Future.successful(Some(PatchChat.ChangeSubject(newSubject))))

      val moveChatToTrashService = chatService
        .patchChat(PatchChatDTO.ChangeSubject(newSubject), genUUID.sample.value, genUUID.sample.value)
      moveChatToTrashService.map(_ mustBe Some(PatchChatDTO.ChangeSubject(newSubject)))
    }
    "return None if the ChatsRepository returns None" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.patchChat(*, *, *)
        .returns(Future.successful(None))

      val moveChatToTrashService = chatService
        .patchChat(PatchChatDTO.MoveToTrash, genUUID.sample.value, genUUID.sample.value)
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

      serviceResponse.map(_ mustBe EmailDTO.toEmailDTO(Some(returnedEmail)))
    }
  }

  "ChatService#getEmail" should {
    "return a ChatDTO with the requested email" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock

      val repositoryChatResponse = genChat.sample.value

      mockChatsRep.getEmail(*, *, *)
        .returns(Future.successful(Some(repositoryChatResponse)))

      val expectedServiceResponse = Some(ChatDTO.toChatDTO(repositoryChatResponse))

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

  "ChatService#postOverseers" should {
    "turn the received optional Set of PostOverseer to one of PostOverseerDTO" in {

      val expectedResponse = genSetPostOverseerDTO.sample

      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.postOverseers(*, *, *)
        .returns(Future.successful(expectedResponse.map(_.map(toPostOverseer))))

      val serviceResponse = chatService.postOverseers(
        genSetPostOverseerDTO.sample.value,
        genUUID.sample.value, genUUID.sample.value)

      serviceResponse.map(_ mustBe expectedResponse)
    }
  }

  "ChatService#getOverseers" should {
    "turn the received optional Set of PostOverseer to one of PostOverseerDTO" in {

      val expectedResponse = genSetPostOverseerDTO.sample

      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.getOverseers(*, *)
        .returns(Future.successful(expectedResponse.map(_.map(toPostOverseer))))

      val serviceResponse = chatService.getOverseers(genUUID.sample.value, genUUID.sample.value)

      serviceResponse.map(_ mustBe expectedResponse)
    }
  }

  "ChatService#deleteOverseer" should {
    "return true if the ChatsRepository returns true" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.deleteOverseer(*, *, *)
        .returns(Future.successful(true))

      val deleteOverseerService = chatService.deleteOverseer(
        genUUID.sample.value, genUUID.sample.value, genUUID.sample.value)
      deleteOverseerService.map(_ mustBe true)
    }
    "return false if the ChatsRepository returns false" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.deleteOverseer(*, *, *)
        .returns(Future.successful(false))

      val deleteOverseerService = chatService.deleteOverseer(
        genUUID.sample.value, genUUID.sample.value, genUUID.sample.value)
      deleteOverseerService.map(_ mustBe false)
    }
  }

}
