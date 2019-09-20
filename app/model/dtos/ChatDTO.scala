package model.dtos

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
          overseerDTO.user,
          overseerDTO.overseers)),
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

  def tupled = (ChatDTO.apply _).tupled
}

