package model.dtos

import play.api.libs.json.{ Json, OFormat }
import repositories.dtos.OversightOLD
import ChatOverseeingDTO._
import ChatOverseenDTO._

case class OversightDtoOLD(overseeing: Set[ChatOverseeingDTO], overseen: Set[ChatOverseenDTO])

object OversightDtoOLD {
  implicit val OversightDtoOFormat: OFormat[OversightDtoOLD] = Json.format[OversightDtoOLD]

  def toOversightDtoOLD(oversight: OversightOLD): OversightDtoOLD =
    OversightDtoOLD(oversight.overseeing.map(toChatOverseeingDTO), oversight.overseen.map(toChatOverseenDTO))

  def toOversightOLD(oversightDTO: OversightDtoOLD): OversightOLD =
    OversightOLD(oversightDTO.overseeing.map(toChatOverseeing), oversightDTO.overseen.map(toChatOverseen))

}
