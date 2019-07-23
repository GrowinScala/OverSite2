package services

import model.dtos.ChatPreviewDTO
import model.types.Mailbox.Inbox
import org.mockito.ArgumentMatchersSugar._
import org.mockito.Mockito.when
import org.scalatest.{ AsyncWordSpec, MustMatchers }
import org.scalatest.mockito.MockitoSugar._
import repositories.ChatsRepository
import repositories.dtos.ChatPreview

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }

class ChatServiceSpec extends AsyncWordSpec with MustMatchers {

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  "ChatService#getChats" should {
    "map ChatPreview DTO" in {
      val mockChatsRep = mock[ChatsRepository]
      when(mockChatsRep.getChatsPreview(any, any))
        .thenReturn(Future.successful(Seq(ChatPreview(1, "Ok", "Ok", "Ok", "Ok"))))

      val chatServiceImpl = new ChatService(mockChatsRep)
      val chatsPreviewDTO = chatServiceImpl.getChats(Inbox, 1)
      chatsPreviewDTO.map(_ mustBe Seq(ChatPreviewDTO(1, "Ok", "Ok", "Ok", "Ok")))
    }
  }

}
