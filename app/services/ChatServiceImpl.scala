package services

import javax.inject.Inject
import model.dtos.ChatPreviewDTO
import model.types.Mailbox
import model.dtos.{ ChatDTO, EmailDTO, OverseersDTO }
import repositories.dtos.{ Chat, ChatPreview }
import repositories.ChatsRepository

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ChatServiceImpl @Inject() (chatsRep: ChatsRepository) extends ChatService {

  def getChats(mailbox: Mailbox, user: Int): Future[Seq[ChatPreviewDTO]] = {
    chatsRep.getChatsPreview(mailbox, user).map(toSeqChatPreviewDTO)
  }

  def getChat(chatId: Int, userId: Int): Future[Option[ChatDTO]] = {
    chatsRep.getChat(chatId, userId).map(toChatDTO)
  }

  //region Auxiliary conversion methods

  private def toChatDTO(optionChat: Option[Chat]): Option[ChatDTO] = {
    optionChat.map {
      chat =>
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
              email.sent != 0,
              email.attachments)).sortBy(_.date))
    }

  }

  private def toSeqChatPreviewDTO(chatPreviews: Seq[ChatPreview]): Seq[ChatPreviewDTO] = {
    chatPreviews.map(chatPreview =>
      ChatPreviewDTO(
        chatPreview.chatId,
        chatPreview.subject,
        chatPreview.lastAddress,
        chatPreview.lastEmailDate,
        chatPreview.contentPreview))
  }

  //endregion

}