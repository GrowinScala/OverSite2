package scala.services

import model.dtos.{ ChatDTO, ChatPreviewDTO }
import model.types.Mailbox
import services.ChatService

import scala.concurrent.Future

class FakeChatService extends ChatService {

  def getChats(mailbox: Mailbox, user: Int): Future[Seq[ChatPreviewDTO]] = {

    Future.successful(Seq(ChatPreviewDTO(1, "Ok", "Ok", "Ok", "Ok")))
  }

  def getChat(chatId: Int, userId: Int): Future[Option[ChatDTO]] =
    ???

}
