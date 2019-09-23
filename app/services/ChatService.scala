package services

import javax.inject.Inject
import model.dtos._
import model.types.Mailbox
import repositories.dtos.{ Chat, ChatPreview, Email }
import repositories.ChatsRepository

import scala.concurrent.{ ExecutionContext, Future }

class ChatService @Inject() (implicit val ec: ExecutionContext, chatsRep: ChatsRepository) {

  def getChats(mailbox: Mailbox, user: String): Future[Seq[ChatPreviewDTO]] = {
    chatsRep.getChatsPreview(mailbox, user).map(toSeqChatPreviewDTO)
  }

  def getChat(chatId: String, userId: String): Future[Option[ChatDTO]] = {
    chatsRep.getChat(chatId, userId).map(ChatDTO.toChatDTO)
  }

  def postChat(createChatDTO: CreateChatDTO, userId: String): Future[CreateChatDTO] = {
    chatsRep.postChat(createChatDTO, userId)
  }

  //Receives a UpsertEmailDTO, returns a CreateChatDTO with the email plus chatId and subject
  def postEmail(upsertEmailDTO: UpsertEmailDTO, chatId: String, userId: String): Future[Option[CreateChatDTO]] = {
    chatsRep.postEmail(upsertEmailDTO, chatId, userId)
  }

  def patchEmail(upsertEmailDTO: UpsertEmailDTO, chatId: String, emailId: String, userId: String): Future[Option[EmailDTO]] = {
    chatsRep.patchEmail(upsertEmailDTO, chatId, emailId, userId).map(EmailDTO.toEmailDTO)
  }

  def patchChat(patchChatDTO: PatchChatDTO, chatId: String, userId: String): Future[Option[PatchChatDTO]] = {
    chatsRep.patchChat(patchChatDTO, chatId, userId)
  }

  def deleteChat(chatId: String, userId: String): Future[Boolean] = {
    chatsRep.deleteChat(chatId, userId)
  }

  def deleteDraft(chatId: String, emailId: String, userId: String): Future[Boolean] = {
    chatsRep.deleteDraft(chatId, emailId, userId)
  }

  def getEmail(chatId: String, emailId: String, userId: String): Future[Option[ChatDTO]] = {
    chatsRep.getEmail(chatId, emailId, userId).map(ChatDTO.toChatDTO)
  }

  //region Auxiliary conversion methods

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