package services

import model.dtos._
import model.types.Mailbox.Inbox
import org.mockito.scalatest.AsyncIdiomaticMockito
import org.scalatest.{ AsyncWordSpec, MustMatchers, OptionValues }
import repositories.ChatsRepository
import repositories.dtos.Email

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

  "ChatService#moveChatToTrash" should {
    "return true if the ChatsRepository returns true" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.moveChatToTrash(*, *)
        .returns(Future.successful(true))

      val moveChatToTrashService = chatService.moveChatToTrash("303c2b72-304e-4bac-84d7-385acb64a616", "148a3b1b-8326-466d-8c27-1bd09b8378f3")
      moveChatToTrashService.map(_ mustBe true)
    }
    "return false if the ChatsRepository returns false" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.moveChatToTrash(*, *)
        .returns(Future.successful(false))

      val moveChatToTrashService = chatService.moveChatToTrash("303c2b72-304e-4bac-84d7-385acb64a616", "148a3b1b-8326-466d-8c27-1bd09b8378f3")
      moveChatToTrashService.map(_ mustBe false)
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

}
