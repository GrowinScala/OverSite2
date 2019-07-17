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

// Put the implementation here instead of the Trait because we're leaving injection for later
class ChatService @Inject() (chatsRep: SlickChatsRepository) {

  def getChats(mailbox: Mailbox, user: Int): Future[Seq[ChatPreviewDTO]] = {
    val chatPreview = chatsRep.getChatPreview(mailbox, user)

    chatPreview.map(_.map(chatPreview =>
      ChatPreviewDTO(chatPreview.chatId, chatPreview.subject, chatPreview.lastAddress, chatPreview.lastEmailDate,
        chatPreview.contentPreview)))

  }

  def getChat(chatId: Int, userId: Int): Future[ChatDTO] = {
    chatsRep.getChat(chatId, userId).map(toChatDTO)
  }

  private def toChatDTO(chat: Chat) = {
    ChatDTO(
      chat.chatId,
      chat.subject,
      chat.addresses,
      chat.overseers.map(overseer =>
        OverseersDTO(
          overseer.user,
          overseer.overseers)),
      chat.emails.map(email =>
        EmailDTO(
          email.emailId,
          email.from,
          email.to,
          email.bcc,
          email.cc,
          email.body,
          email.date,
          intToBoolean(email.sent),
          email.attachments)).sortBy(_.date))
  }
  private def intToBoolean(i: Int): Boolean = if (i != 0) true else false

}