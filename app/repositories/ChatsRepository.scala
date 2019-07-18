package repositories

import model.types.Mailbox
import repositories.dtos.{ Chat, ChatPreview }

import scala.concurrent.Future

trait ChatsRepository {

  def getChatsPreview(mailbox: Mailbox, user: Int): Future[Seq[ChatPreview]]

  def getChat(chatId: Int, userId: Int): Future[Option[Chat]]

}
