package model.dtos

import controllers.AuthenticatedUser
import play.api.libs.json.{ Json, OFormat }
import repositories.dtos.ChatPreview
import controllers.ChatController._
import model.types.Sort.DEFAULT_SORT
import model.types.{ Page, PerPage, Sort }
import play.api.mvc.AnyContent
import repositories.RepUtils.RepConstants.{ DEFAULT_PAGE, DEFAULT_PER_PAGE }
import repositories.RepUtils.types.OrderBy.DefaultOrder

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

  def toSeqChatPreviewDTO(chatPreviews: Seq[ChatPreview], auth: AuthenticatedUser[AnyContent]): Seq[ChatPreviewDTO] = {
    chatPreviews.map(chatPreview =>
      ChatPreviewDTO(
        chatPreview.chatId,
        makeGetChatLink(chatPreview.chatId, Page(DEFAULT_PAGE), PerPage(DEFAULT_PER_PAGE),
          Sort(DEFAULT_SORT, DefaultOrder), auth),
        chatPreview.subject,
        chatPreview.lastAddress,
        chatPreview.lastEmailDate,
        chatPreview.contentPreview))
  }

}

