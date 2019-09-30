package model.dtos

import play.api.libs.json.{Json, OFormat}
import repositories.dtos.Oversight
import ChatOverseeingDTO._
import ChatOversoughtDTO._

case class OversightDTO (overseeing: Set[ChatOverseeingDTO], oversought: Set[ChatOversoughtDTO])

object OversightDTO {
	implicit val OversightDtoOFormat: OFormat[OversightDTO] = Json.format[OversightDTO]
	
	def toOversightDTO(oversight: Oversight): OversightDTO =
		OversightDTO(oversight.overseeing.map(toChatOverseeingDTO), oversight.oversought.map(toChatOversoughtDTO))
}
