package services

import model.dtos.{ ChatDTO, ChatPreviewDTO, EmailDTO, OverseersDTO }
import model.types.Mailbox.Inbox
import org.mockito.ArgumentMatchersSugar._
import org.mockito.Mockito.when
import org.scalatest.{ AsyncWordSpec, MustMatchers }
import org.scalatest.mockito.MockitoSugar._
import repositories.ChatsRepository
import repositories.dtos._

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }

class ChatServiceSpec extends AsyncWordSpec with MustMatchers {

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  "ChatService#getChats" should {
    "map ChatPreview DTO" in {
      val mockChatsRep = mock[ChatsRepository]
      when(mockChatsRep.getChatsPreview(any, any))
        .thenReturn(Future.successful(Seq(ChatPreview(1, "Ok", "Ok", "Ok", "Ok"))))

      val chatServiceImpl = new ChatServiceImpl(mockChatsRep)
      val chatsPreviewDTO = chatServiceImpl.getChats(Inbox, 1)
      chatsPreviewDTO.map(_ mustBe Seq(ChatPreviewDTO(1, "Ok", "Ok", "Ok", "Ok")))
    }
  }

  "ChatService#getChat" should {
    "map Chat DTO" in {
      val mockChatsRep = mock[ChatsRepository]
      when(mockChatsRep.getChat(any, any))
        .thenReturn(Future.successful(
          Some(
            Chat(
              1, "Subject", Set("address1", "address2"),
              Set(Overseers("address1", Set("address3"))),
              Seq(Email(1, "address1", Set("address2"), Set(), Set(),
                "This is the body", "2019-07-19 10:00:00", 1, Set(1)))))))

      val chatServiceImpl = new ChatServiceImpl(mockChatsRep)
      val chatDTO = chatServiceImpl.getChat(chatId = 1, userId = 1)
      val expectedServiceResponse =
        Some(
          ChatDTO(
            1, "Subject", Set("address1", "address2"),
            Set(OverseersDTO("address1", Set("address3"))),
            Seq(EmailDTO(1, "address1", Set("address2"), Set(), Set(),
              "This is the body", "2019-07-19 10:00:00", true, Set(1)))))
      chatDTO.map(_ mustBe expectedServiceResponse)
    }
  }

}
