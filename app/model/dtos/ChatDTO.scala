package model.dtos

import play.api.libs.json.{ Json, OFormat }
import repositories.dtos.Chat

case class ChatDTO(chatId: Int, subject: String, addresses: Seq[String],
  overseers: Seq[OverseersDTO], emails: Seq[EmailDTO])

object ChatDTO {
  implicit val chatFormat: OFormat[ChatDTO] = Json.format[ChatDTO]

  def tupled = (ChatDTO.apply _).tupled
}

