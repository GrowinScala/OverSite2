package repositories

import model.dtos._
import model.types.Mailbox
import repositories.dtos._

import scala.concurrent.Future

trait ChatsRepository {

  def getChatsPreview(mailbox: Mailbox, user: String): Future[Seq[ChatPreview]]

  def getChat(chatId: String, userId: String): Future[Option[Chat]]

  def postChat(createChatDTO: CreateChatDTO, userId: String): Future[CreateChatDTO]

  def postEmail(upsertEmailDTO: UpsertEmailDTO, chatId: String, userId: String): Future[Option[CreateChatDTO]]

  def patchEmail(upsertEmailDTO: UpsertEmailDTO, chatId: String, emailId: String, userId: String): Future[Option[Email]]

  def patchChat(patchChatDTO: PatchChatDTO, chatId: String, userId: String): Future[Option[PatchChatDTO]]

  def deleteChat(chatId: String, userId: String): Future[Boolean]

  def getEmail(chatId: String, emailId: String, userId: String): Future[Option[Chat]]

  def deleteDraft(chatId: String, emailId: String, userId: String): Future[Boolean]

  def postOverseers(postOverseers: Set[PostOverseer], chatId: String, userId: String): Future[Option[Set[PostOverseer]]]

  def deleteOverseer(chatId: String, oversightId: String, userId: String): Future[Boolean]
}
