package model.dtos

import play.api.libs.json.{ Json, OFormat }
import repositories.dtos.Oversight
import ChatOverseeingDTO._
import ChatOverseenDTO._

case class OversightDTO(optOverseeing: Option[ChatOverseeingDTO], optOverseen: Option[ChatOverseenDTO])

object OversightDTO {
	implicit val OversightDtoOFormat: OFormat[OversightDTO] = Json.format[OversightDTO]
	
	def toOversightDTO(oversight: Oversight): OversightDTO =
		OversightDTO(oversight.optOverseeing.map(toChatOverseeingDTO), oversight.optOverseen.map(toChatOverseenDTO))
	
	def toOversight(oversightDTO: OversightDTO): Oversight =
		Oversight(oversightDTO.optOverseeing.map(toChatOverseeing), oversightDTO.optOverseen.map(toChatOverseen))
	
}
