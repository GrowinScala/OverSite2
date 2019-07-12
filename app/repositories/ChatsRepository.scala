package repositories

import model.types.Mailbox
import repositories.dtos.ChatPreview

import scala.concurrent.Future

trait ChatsRepository {

  def getChatPreview(mailbox: Mailbox, user: Int): Future[Seq[ChatPreview]]

}
