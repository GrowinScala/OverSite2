package services

import javax.inject.Inject
import model.dtos.ChatPreviewDTO
import model.types.Mailbox
import model.types.Mailbox._
import model.dtos.{ ChatDTO, EmailDTO, OverseersDTO }
import repositories.dtos.Chat
import repositories.slick.implementations.SlickChatsRepository
import repositories.ChatsRepository

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ChatServiceImpl @Inject() (chatsRep: ChatsRepository) extends ChatService {

  def getChats(mailbox: Mailbox, user: Int): Future[Seq[ChatPreviewDTO]] = {
    val chatsPreview = chatsRep.getChatsPreview(mailbox, user)

    chatsPreview.map(_.map(chatPreview =>
      ChatPreviewDTO(chatPreview.chatId, chatPreview.subject, chatPreview.lastAddress, chatPreview.lastEmailDate,
        chatPreview.contentPreview)))
  }

  def getChat(chatId: Int, userId: Int): Future[Option[ChatDTO]] = {
    chatsRep.getChat(chatId, userId).map(ChatDTO.toChatDTO)
  }
}