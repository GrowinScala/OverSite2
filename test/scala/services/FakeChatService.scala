package scala.services

import model.dtos.{ ChatDTO, ChatPreviewDTO, EmailDTO, OverseersDTO }
import model.types.Mailbox
import services.ChatService

import scala.concurrent.{ ExecutionContext, Future }

class FakeChatService extends ChatService {
  implicit val ec = ExecutionContext.global

  def getChats(mailbox: Mailbox, user: Int): Future[Seq[ChatPreviewDTO]] = {

    Future.successful(Seq(ChatPreviewDTO(1, "Ok", "Ok", "Ok", "Ok")))
  }

  def getChat(chatId: Int, userId: Int): Future[Option[ChatDTO]] =
    if (chatId < 0 || userId < 0)
      Future(
        Some(
          ChatDTO(1, "Subject",
            Seq("address1, address2"),
            Seq(OverseersDTO("address1", Seq("address3"))),
            Seq(
              EmailDTO(
                1, "address1",
                Seq("address2"), Seq(), Seq(),
                "This is the body", "2019-07-19 10:00:00",
                true, Seq(1))))))
    else Future(None)
}
