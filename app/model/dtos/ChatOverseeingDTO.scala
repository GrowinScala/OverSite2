package model.dtos

import play.api.libs.json.{Json, OFormat}
import repositories.dtos.ChatOverseeing
import OverseeingDTO._

case class ChatOverseeingDTO(chatId: String, overseeings: Set[OverseeingDTO])

object ChatOverseeingDTO {
	implicit val ChatOverseeingDtoOFormat: OFormat[ChatOverseeingDTO] = Json.format[ChatOverseeingDTO]
	
	def toChatOverseeingDTO(chatOverseeing: ChatOverseeing): ChatOverseeingDTO =
		ChatOverseeingDTO(chatOverseeing.chatId, chatOverseeing.overseeings.map(toOverseeingDTO))
}