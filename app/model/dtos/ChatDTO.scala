package model.dtos

import controllers.AuthenticatedUser
import controllers.ChatController.makeGetEmailLink
import play.api.libs.json.{ Json, OFormat, OWrites, Reads }
import repositories.dtos.{ Chat, Email, Overseers }

case class ChatDTO(chatId: String, subject: String, addresses: Set[String],
  overseers: Set[OverseersDTO], emails: Seq[EmailDTO])

object ChatDTO {
  //implicit val chatFormat: OFormat[ChatDTO] = Json.format[ChatDTO]

  //implicit val overseersReads: Reads[OverseersDTO] = Json.reads[OverseersDTO]
  implicit val overseersWrites: OWrites[OverseersDTO] = Json.writes[OverseersDTO]

  //implicit val emailReads: Reads[EmailDTO] = Json.reads[EmailDTO]
  implicit val emailWrites: OWrites[EmailDTO] = Json.writes[EmailDTO]

  //implicit val chatReads: Reads[ChatDTO] = Json.reads[ChatDTO]
  implicit val chatWrites: OWrites[ChatDTO] = Json.writes[ChatDTO]

  def toChat(chatDTO: ChatDTO): Chat =
    Chat(
      chatDTO.chatId,
      chatDTO.subject,
      chatDTO.addresses,
      chatDTO.overseers.map(overseerDTO =>
        Overseers(
          overseerDTO.overseeAddress,
          overseerDTO.overseersAddresses)),
      chatDTO.emails.map(emailDTO =>
        Email(
          emailDTO.emailId,
          emailDTO.from,
          emailDTO.to,
          emailDTO.bcc,
          emailDTO.cc,
          emailDTO.body,
          emailDTO.date,
          if (emailDTO.sent) 1
          else 0,
          emailDTO.attachments)).sortBy(_.date))

  def toChatDTO(chat: Chat, auth: AuthenticatedUser[Any]): ChatDTO = {
    ChatDTO(
      chat.chatId,
      chat.subject,
      chat.addresses,
      chat.overseers.map(overseer =>
        OverseersDTO(
          overseer.overseeAddress,
          overseer.overseersAddresses)),
      chat.emails.map(email =>
        EmailDTO(
          email.emailId,
          makeGetEmailLink(chat.chatId, email.emailId, auth),
          email.from,
          email.to,
          email.bcc,
          email.cc,
          email.body,
          email.date,
          email.sent != 0,
          email.attachments)))
  }

  def tupled = (ChatDTO.apply _).tupled
}

