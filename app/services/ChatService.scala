package services

import model.dtos.{ ChatDTO, ChatPreviewDTO }
import model.types.Mailbox

import scala.concurrent.Future

trait ChatService {

  def getChats(mailbox: Mailbox, user: Int): Future[Seq[ChatPreviewDTO]]

  def getChat(chatId: Int, userId: Int): Future[Option[ChatDTO]]

}
