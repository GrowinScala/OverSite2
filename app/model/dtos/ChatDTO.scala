package model.dtos

import play.api.libs.json.{ Json, OFormat }

case class ChatDTO(chatId: String, subject: String, addresses: Seq[String],
  overseers: Seq[OverseersDTO], emails: Seq[EmailDTO])

object ChatDTO {
  implicit val chatFormat: OFormat[ChatDTO] = Json.format[ChatDTO]

  def tupled = (ChatDTO.apply _).tupled

}

