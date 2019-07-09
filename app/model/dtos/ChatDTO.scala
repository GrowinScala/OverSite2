package model.dtos

import play.api.libs.json.{Json, OFormat}

case class ChatDTO (chatId: Int, subject: String, addresses: Array[String],
                    overseers: Array[OverseersDTO], emails: Array[EmailDTO])

object ChatDTO {
  implicit val chatFormat : OFormat[ChatDTO] = Json.format[ChatDTO]

  def tupled = (ChatDTO.apply _).tupled

}

