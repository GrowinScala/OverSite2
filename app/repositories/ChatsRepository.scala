package repositories

import model.types.Mailbox
import repositories.dtos.{ Chat, ChatPreview }

import scala.concurrent.Future

trait ChatsRepository {
  def getChat(chatId: Int, userId: Int): Future[Chat]

  def getChatPreview(mailbox: Mailbox, user: Int): Future[Seq[ChatPreview]]
}
