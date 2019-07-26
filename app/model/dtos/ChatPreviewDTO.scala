package model.dtos

import play.api.libs.json.{ Json, OFormat }

case class ChatPreviewDTO(chatId: String, subject: String, lastAddress: String,
  lastEmailDate: String, contentPreview: String)

object ChatPreviewDTO {
  implicit val chatPreviewFormat: OFormat[ChatPreviewDTO] = Json.format[ChatPreviewDTO]

  def tupled = (ChatPreviewDTO.apply _).tupled

}

