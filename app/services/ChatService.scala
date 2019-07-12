package services

import javax.inject.Inject
import model.dtos.ChatPreviewDTO
import model.types.Mailbox
import model.types.Mailbox._
import repositories.slick.implementations.SlickChatsRepository
import repositories.ChatsRepository

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

// Put the implementation here instead of the Trait because we're leaving injection for later
class ChatService @Inject() (chatsRep: SlickChatsRepository) {

  def getChats(mailbox: Mailbox, user: Int): Future[Seq[ChatPreviewDTO]] = {
    val chatPreview = chatsRep.getChatPreview(mailbox, user)

    chatPreview.map(_.map(chatPreview =>
      ChatPreviewDTO(chatPreview.chatId, chatPreview.subject, chatPreview.lastAddress, chatPreview.lastEmailDate,
        chatPreview.contentPreview)))

  }

}
