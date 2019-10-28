package repositories

import model.types.Mailbox
import repositories.dtos._

import scala.concurrent.Future

trait ChatsRepository {

  def getChatsPreview(mailbox: Mailbox, page: Int, perPage: Int,
    user: String): Future[Option[(Seq[ChatPreview], Int, Int)]]

  def getChat(chatId: String, page: Int, perPage: Int, userId: String): Future[Either[String, (Chat, Int, Int)]]

  def getOverseers(chatId: String, page: Int, perPage: Int,
    userId: String): Future[Either[String, (Seq[PostOverseer], Int, Int)]]

  def postChat(createChat: CreateChat, userId: String): Future[Option[CreateChat]]

  def postEmail(upsertEmail: UpsertEmail, chatId: String, userId: String): Future[Option[CreateChat]]

  def patchEmail(upsertEmailDTO: UpsertEmail, chatId: String, emailId: String, userId: String): Future[Option[Email]]

  def patchChat(patchChat: PatchChat, chatId: String, userId: String): Future[Option[PatchChat]]

  def deleteChat(chatId: String, userId: String): Future[Boolean]

  def getEmail(chatId: String, emailId: String, userId: String): Future[Option[Chat]]

  def deleteDraft(chatId: String, emailId: String, userId: String): Future[Boolean]

  def postOverseers(postOverseers: Set[PostOverseer], chatId: String, userId: String): Future[Option[Set[PostOverseer]]]

  def deleteOverseer(chatId: String, oversightId: String, userId: String): Future[Boolean]

  def getOversights(userId: String): Future[Option[Oversight]]

  def getOverseeings(page: Int, perPage: Int, userId: String): Future[Option[(Seq[ChatOverseeing], Int, Int)]]

  def getOverseens(page: Int, perPage: Int, userId: String): Future[Option[(Seq[ChatOverseen], Int, Int)]]

  def postAttachment(chatId: String, emailId: String, userId: String, filename: String, attachmentPath: String): Future[Option[String]]

  def verifyDraftPermissions(chatId: String, emailId: String, userId: String): Future[Boolean]
}
