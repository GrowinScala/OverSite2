package services

import model.dtos.ChatPreviewDTO
import model.types.Mailbox.Inbox
import org.mockito.Mockito.when
import org.mockito.scalatest.AsyncIdiomaticMockito
import org.scalatest.{ AsyncWordSpec, MustMatchers }
import repositories.ChatsRepository
import repositories.dtos.ChatPreview

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }

class ChatServiceSpec extends AsyncWordSpec with AsyncIdiomaticMockito with MustMatchers {

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  "ChatService#getChats" should {
    "map ChatPreview DTO" in {
      val mockChatsRep = mock[ChatsRepository]
      when(mockChatsRep.getChatsPreview(*, *))
        .thenReturn(Future.successful(Seq(ChatPreview("00000000-0000-0000-0000-000000000000", "Ok", "Ok", "Ok", "Ok"))))

      val chatServiceImpl = new ChatService(mockChatsRep)
      val chatsPreviewDTO = chatServiceImpl.getChats(Inbox, "00000000-0000-0000-0000-000000000000")
      chatsPreviewDTO.map(_ mustBe Seq(ChatPreviewDTO("00000000-0000-0000-0000-000000000000", "Ok", "Ok", "Ok", "Ok")))
    }
  }

}
