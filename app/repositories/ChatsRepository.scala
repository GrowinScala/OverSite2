package repositories

import model.dtos.CreateChatDTO
import model.types.Mailbox
import repositories.dtos.{ Chat, ChatPreview }

import scala.concurrent.Future

trait ChatsRepository {

  def getChatsPreview(mailbox: Mailbox, user: String): Future[Seq[ChatPreview]]

  def getChat(chatId: String, userId: String): Future[Option[Chat]]

  def postChat(createChatDTO: CreateChatDTO, userId: String): Future[Option[CreateChatDTO]]

}
