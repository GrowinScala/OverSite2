package model.dtos

import play.api.libs.json.{ Json, OFormat }
import repositories.dtos.Oversight
import ChatOverseeingDTO._
import ChatOverseenDTO._

case class OversightDTO(overseeing: Set[ChatOverseeingDTO], overseen: Set[ChatOverseenDTO])

object OversightDTO {
  implicit val OversightDtoOFormat: OFormat[OversightDTO] = Json.format[OversightDTO]

  def toOversightDTO(oversight: Oversight): OversightDTO =
    OversightDTO(oversight.overseeing.map(toChatOverseeingDTO), oversight.overseen.map(toChatOverseenDTO))

  def toOversight(oversightDTO: OversightDTO): Oversight =
    Oversight(oversightDTO.overseeing.map(toChatOverseeing), oversightDTO.overseen.map(toChatOverseen))

}
