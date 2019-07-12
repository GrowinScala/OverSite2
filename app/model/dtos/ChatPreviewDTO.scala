package model.dtos

import play.api.libs.json.{ Json, OFormat }

case class ChatPreviewDTO(chatId: Int, subject: String, lastAddress: String,
  lastEmailDate: String, contentPreview: String)

object ChatPreviewDTO {
  implicit val addressFormat: OFormat[ChatPreviewDTO] = Json.format[ChatPreviewDTO]

  def tupled = (ChatPreviewDTO.apply _).tupled

}

