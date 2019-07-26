package model.dtos

import play.api.libs.json.{ Json, OFormat, OWrites, Reads }

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

  def tupled = (ChatDTO.apply _).tupled
}

