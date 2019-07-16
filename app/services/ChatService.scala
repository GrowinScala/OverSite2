package services

import javax.inject.Inject
import model.dtos.ChatsPreviewDTO
import model.types.Mailbox
import model.types.Mailbox._
import repositories.slick.implementations.SlickChatsRepository
import repositories.ChatsRepository

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

// Put the implementation here instead of the Trait because we're leaving injection for later
class ChatService @Inject() (chatsRep: ChatsRepository) {

  def getChats(mailbox: Mailbox, user: Int): Future[Seq[ChatsPreviewDTO]] = {
    val chatsPreview = chatsRep.getChatsPreview(mailbox, user)

    chatsPreview.map(_.map(chatPreview =>
      ChatsPreviewDTO(chatPreview.chatId, chatPreview.subject, chatPreview.lastAddress, chatPreview.lastEmailDate,
        chatPreview.contentPreview)))

  }

}
