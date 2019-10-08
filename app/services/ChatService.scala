package services

import javax.inject.Inject
import model.dtos._
import model.types.{ Mailbox, Page, PerPage }
import repositories.ChatsRepository
import PostOverseerDTO._
import OversightDTO._
import ChatPreviewDTO._
import repositories.utils.RepMessages._
import utils.Jsons._

import scala.concurrent.{ ExecutionContext, Future }

class ChatService @Inject() (implicit val ec: ExecutionContext, chatsRep: ChatsRepository) {

  def getChats(mailbox: Mailbox, page: Page, perPage: PerPage,
    userId: String): Future[Option[(Seq[ChatPreviewDTO], Int, Page)]] =
    chatsRep.getChatsPreview(mailbox, page.value, perPage.value, userId)
      .map(_.map {
        case (chatsPreview, totalCount, lastPage) =>
          (toSeqChatPreviewDTO(chatsPreview), totalCount, Page(lastPage))
      })

  def getOverseers(chatId: String, page: Page, perPage: PerPage,
    userId: String): Future[Either[Error, (Seq[PostOverseerDTO], Int, Page)]] =

    chatsRep.getOverseers(chatId, page.value, perPage.value, userId).map {
      case Right((postOverseers, totalCount, lastPage)) =>
        Right((toSeqPostOverseerDTO(postOverseers), totalCount, Page(lastPage)))
      case Left(`CHAT_NOT_FOUND`) => Left(chatNotFound)
      case Left(_) => Left(internalError)
    }

  def getChat(chatId: String, userId: String): Future[Option[ChatDTO]] = {
    chatsRep.getChat(chatId, userId).map(_.map(ChatDTO.toChatDTO))
  }

  def postChat(createChatDTO: CreateChatDTO, userId: String): Future[Option[CreateChatDTO]] = {
    chatsRep.postChat(CreateChatDTO.toCreateChat(createChatDTO), userId).map(_.map(CreateChatDTO.toCreateChatDTO))
  }

  def postEmail(upsertEmailDTO: UpsertEmailDTO, chatId: String, userId: String): Future[Option[CreateChatDTO]] = {
    chatsRep.postEmail(UpsertEmailDTO.toUpsertEmail(upsertEmailDTO), chatId, userId)
      .map(_.map(CreateChatDTO.toCreateChatDTO))
  }

  def patchEmail(upsertEmailDTO: UpsertEmailDTO, chatId: String, emailId: String,
    userId: String): Future[Option[EmailDTO]] = {
    chatsRep.patchEmail(UpsertEmailDTO.toUpsertEmail(upsertEmailDTO), chatId, emailId, userId)
      .map(EmailDTO.toEmailDTO)
  }

  def patchChat(patchChatDTO: PatchChatDTO, chatId: String, userId: String): Future[Option[PatchChatDTO]] = {
    chatsRep.patchChat(PatchChatDTO.toPatchChat(patchChatDTO), chatId, userId).map(_.map(PatchChatDTO.toPatchChatDTO))
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

  def postOverseers(postOverseersDTO: Set[PostOverseerDTO], chatId: String,
    userId: String): Future[Option[Set[PostOverseerDTO]]] =
    chatsRep.postOverseers(toSetPostOverseer(postOverseersDTO), chatId, userId)
      .map(_.map(toSetPostOverseerDTO))

  def deleteOverseer(chatId: String, oversightId: String, userId: String): Future[Boolean] =
    chatsRep.deleteOverseer(chatId, oversightId, userId)

  def getOversightsOLD(userId: String): Future[OversightDTO] =
    chatsRep.getOversights(userId)
      .map(toOversightDTO)
  
  def getOversights(userId: String): Future[OversightDTO] =
    chatsRep.getOversights(userId)
      .map(toOversightDTO)
}