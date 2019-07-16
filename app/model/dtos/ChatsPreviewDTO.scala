package model.dtos

import play.api.libs.json.{ Json, OFormat }

case class ChatsPreviewDTO(chatId: Int, subject: String, lastAddress: String,
  lastEmailDate: String, contentPreview: String)

object ChatsPreviewDTO {
  implicit val addressFormat: OFormat[ChatsPreviewDTO] = Json.format[ChatsPreviewDTO]

  def tupled = (ChatsPreviewDTO.apply _).tupled

}

