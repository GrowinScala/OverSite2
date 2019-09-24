package services

import javax.inject.Inject
import model.dtos._
import model.types.Mailbox
import repositories.ChatsRepository

import scala.concurrent.{ ExecutionContext, Future }

class ChatService @Inject() (implicit val ec: ExecutionContext, chatsRep: ChatsRepository) {

  def getChats(mailbox: Mailbox, user: String): Future[Seq[ChatPreviewDTO]] = {
    chatsRep.getChatsPreview(mailbox, user).map(ChatPreviewDTO.toSeqChatPreviewDTO)
  }

  def getChat(chatId: String, userId: String): Future[Option[ChatDTO]] = {
    chatsRep.getChat(chatId, userId).map(_.map(ChatDTO.toChatDTO))
  }

  def postChat(createChatDTO: CreateChatDTO, userId: String): Future[CreateChatDTO] = {
    chatsRep.postChat(CreateChatDTO.toCreateChat(createChatDTO), userId).map(CreateChatDTO.toCreateChatDTO)
  }

  def postEmail(upsertEmailDTO: UpsertEmailDTO, chatId: String, userId: String): Future[Option[CreateChatDTO]] = {
    chatsRep.postEmail(UpsertEmailDTO.toUpsertEmail(upsertEmailDTO), chatId, userId).map(_.map(CreateChatDTO.toCreateChatDTO))
  }

  def patchEmail(upsertEmailDTO: UpsertEmailDTO, chatId: String, emailId: String, userId: String): Future[Option[EmailDTO]] = {
    chatsRep.patchEmail(UpsertEmailDTO.toUpsertEmail(upsertEmailDTO), chatId, emailId, userId).map(EmailDTO.toEmailDTO)
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
    chatsRep.getEmail(chatId, emailId, userId).map(_.map(ChatDTO.toChatDTO))
  }
}