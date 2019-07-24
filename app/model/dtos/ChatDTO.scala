package model.dtos

import play.api.libs.json.{ Json, OFormat }
import repositories.dtos.Chat

case class ChatDTO(chatId: Int, subject: String, addresses: Seq[String],
  overseers: Seq[OverseersDTO], emails: Seq[EmailDTO])

object ChatDTO {
  implicit val chatFormat: OFormat[ChatDTO] = Json.format[ChatDTO]

  def tupled = (ChatDTO.apply _).tupled

  def toChatDTO(optionChat: Option[Chat]) = {
    optionChat.map {
      chat =>
        ChatDTO(
          chat.chatId,
          chat.subject,
          chat.addresses,
          chat.overseers.map(overseer =>
            OverseersDTO(
              overseer.user,
              overseer.overseers)),
          chat.emails.map(email =>
            EmailDTO(
              email.emailId,
              email.from,
              email.to,
              email.bcc,
              email.cc,
              email.body,
              email.date,
              email.sent != 0,
              email.attachments)).sortBy(_.date))
    }

  }
}

