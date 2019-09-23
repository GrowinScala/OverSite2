package repositories

import model.dtos.PatchChatDTO
import model.types.Mailbox
import repositories.dtos._

import scala.concurrent.Future

trait ChatsRepository {

  def getChatsPreview(mailbox: Mailbox, user: String): Future[Seq[ChatPreview]]

  def getChat(chatId: String, userId: String): Future[Option[Chat]]

  def postChat(createChat: CreateChat, userId: String): Future[CreateChat]

  def postEmail(upsertEmail: UpsertEmail, chatId: String, userId: String): Future[Option[CreateChat]]

  def patchEmail(upsertEmailDTO: UpsertEmail, chatId: String, emailId: String, userId: String): Future[Option[Email]]

  def patchChat(patchChatDTO: PatchChatDTO, chatId: String, userId: String): Future[Option[PatchChatDTO]]

  def deleteChat(chatId: String, userId: String): Future[Boolean]

  def getEmail(chatId: String, emailId: String, userId: String): Future[Option[Chat]]

  def deleteDraft(chatId: String, emailId: String, userId: String): Future[Boolean]
}
