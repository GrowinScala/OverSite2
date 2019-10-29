package model.dtos

import play.api.libs.json.{ Json, OFormat }
import repositories.dtos.ChatPreview
import controllers.ChatController

case class ChatPreviewDTO(chatId: String, chatLink: String, subject: String, lastAddress: String,
  lastEmailDate: String, contentPreview: String)

object ChatPreviewDTO {
  implicit val chatPreviewFormat: OFormat[ChatPreviewDTO] = Json.format[ChatPreviewDTO]

  def tupled = (ChatPreviewDTO.apply _).tupled

  def toSeqChatPreview(chatPreviewDTOSeq: Seq[ChatPreviewDTO]): Seq[ChatPreview] =
    chatPreviewDTOSeq.map(chatPreviewDTO =>
      ChatPreview(
        chatPreviewDTO.chatId,
        chatPreviewDTO.subject,
        chatPreviewDTO.lastAddress,
        chatPreviewDTO.lastEmailDate,
        chatPreviewDTO.contentPreview))

  def toSeqChatPreviewDTO(chatPreviews: Seq[ChatPreview]): Seq[ChatPreviewDTO] = {
    chatPreviews.map(chatPreview =>
      ChatPreviewDTO(
        chatPreview.chatId,
        makeGetChatsLink
        chatPreview.subject,
        chatPreview.lastAddress,
        chatPreview.lastEmailDate,
        chatPreview.contentPreview))
  }

}

