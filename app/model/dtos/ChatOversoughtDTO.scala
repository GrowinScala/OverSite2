package model.dtos

import play.api.libs.json.{Json, OFormat}
import repositories.dtos.ChatOversought
import OversoughtDTO._

case class ChatOversoughtDTO(chatId: String, oversoughts: Set[OversoughtDTO])

object ChatOversoughtDTO {
	implicit val ChatOversoughtDtoOFormat: OFormat[ChatOversoughtDTO] = Json.format[ChatOversoughtDTO]
	
	def toChatOversoughtDTO(chatOversought: ChatOversought): ChatOversoughtDTO =
		ChatOversoughtDTO(chatOversought.chatId, chatOversought.oversoughts.map(toOversoughtDTO))
}