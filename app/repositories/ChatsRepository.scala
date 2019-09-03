package repositories

import model.dtos.{ CreateChatDTO, EmailDTO, UpsertEmailDTO }
import model.types.Mailbox
import repositories.dtos.{ Chat, ChatPreview, Email }

import scala.concurrent.Future

trait ChatsRepository {

  def getChatsPreview(mailbox: Mailbox, user: String): Future[Seq[ChatPreview]]

  def getChat(chatId: String, userId: String): Future[Option[Chat]]

  def postChat(createChatDTO: CreateChatDTO, userId: String): Future[CreateChatDTO]

  def postEmail(createEmailDTO: UpsertEmailDTO, chatId: String, userId: String): Future[Option[CreateChatDTO]]

  def patchEmail(upsertEmailDTO: UpsertEmailDTO, chatId: String, emailId: String, userId: String): Future[Option[Email]]

  def moveChatToTrash(chatId: String, userId: String): Future[Boolean]

  def getEmail(chatId: String, emailId: String, userId: String): Future[Option[Chat]]
}
