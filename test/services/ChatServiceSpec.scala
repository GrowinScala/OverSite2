package services

import model.dtos._
import model.types.Mailbox.Inbox
import org.mockito.scalatest.AsyncIdiomaticMockito
import org.scalatest.{ AsyncWordSpec, MustMatchers, OptionValues }
import repositories.ChatsRepository

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
      val createEmailDTO = genCreateEmailDTOption.sample.value.copy(emailId = None)

      val expectedResponse = genCreateChatDTOption.sample.value.copy(
        email = createEmailDTO.copy(emailId = Some(genUUID.sample.value)))

      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.postEmail(*, *, *)
        .returns(Future.successful(Some(expectedResponse)))

      val serviceResponse = chatService.postEmail(createEmailDTO, genUUID.sample.value, genUUID.sample.value)

      serviceResponse.map(_ mustBe Some(expectedResponse))
    }
  }

}
