package repositories

import model.dtos._
import model.types.Mailbox
import repositories.dtos._

import scala.concurrent.Future

trait ChatsRepository {

  def getChatsPreview(mailbox: Mailbox, user: String): Future[Seq[ChatPreview]]

  def getChat(chatId: String, userId: String): Future[Option[Chat]]

  def postChat(createChat: CreateChat, userId: String): Future[CreateChat]

  def postEmail(upsertEmail: UpsertEmail, chatId: String, userId: String): Future[Option[CreateChat]]

  def patchEmail(upsertEmailDTO: UpsertEmail, chatId: String, emailId: String, userId: String): Future[Option[Email]]

  def patchChat(patchChat: PatchChat, chatId: String, userId: String): Future[Option[PatchChat]]

  def deleteChat(chatId: String, userId: String): Future[Boolean]

  def getEmail(chatId: String, emailId: String, userId: String): Future[Option[Chat]]

  def deleteDraft(chatId: String, emailId: String, userId: String): Future[Boolean]

  def postOverseers(postOverseers: Set[PostOverseer], chatId: String, userId: String): Future[Option[Set[PostOverseer]]]

  def getOverseers(chatId: String, userId: String): Future[Option[Set[PostOverseer]]]
}
