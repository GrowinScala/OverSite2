package model.dtos

import play.api.libs.json.{Json, OFormat}
import repositories.dtos.Oversought

case class OversoughtDTO(oversightId: String, overseerAddress: String)

object OversoughtDTO {
	implicit val OversoughtDtoOFormat: OFormat[OversoughtDTO] = Json.format[OversoughtDTO]
	
	def toOversoughtDTO(oversought: Oversought): OversoughtDTO =
		OversoughtDTO(oversought.oversightId, oversought.overseerAddress)
}
