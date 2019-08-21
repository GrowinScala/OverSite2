package services

import javax.inject.Inject
import model.dtos._
import model.types.Mailbox
import repositories.dtos.{ Chat, ChatPreview }
import repositories.ChatsRepository

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ChatService @Inject() (chatsRep: ChatsRepository) {

  def getChats(mailbox: Mailbox, user: String): Future[Seq[ChatPreviewDTO]] = {
    chatsRep.getChatsPreview(mailbox, user).map(toSeqChatPreviewDTO)
  }

  def getChat(chatId: String, userId: String): Future[Option[ChatDTO]] = {
    chatsRep.getChat(chatId, userId).map(toChatDTO)
  }

  def postChat(createChatDTO: CreateChatDTO, userId: String): Future[CreateChatDTO] = {
    chatsRep.postChat(createChatDTO, userId)
  }

  //Receives a CreateEmailDTO, returns a CreateChatDTO with the email plus chatId and subject
  def postEmail(createEmailDTO: UpsertEmailDTO, chatId: String, userId: String): Future[Option[CreateChatDTO]] = {
    chatsRep.postEmail(createEmailDTO, chatId, userId)
  }

  def patchEmail(upsertEmailDTO: UpsertEmailDTO, chatId: String, emailId: String, userId: String): Future[Option[UpsertEmailDTO]] = {
    chatsRep.patchEmail(upsertEmailDTO, chatId, emailId, userId)
  }

  def moveChatToTrash(chatId: String, userId: String): Future[Boolean] = {
    chatsRep.moveChatToTrash(chatId, userId)
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