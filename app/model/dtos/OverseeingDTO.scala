package model.dtos

import play.api.libs.functional.syntax.unlift
import play.api.libs.json.{ JsPath, Json, OFormat }
import play.api.libs.json.Reads.email
import repositories.dtos.Overseeing

case class OverseeingDTO(oversightId: String, overseeAddress: String)

object OverseeingDTO {
  implicit val OverseeingDtoOFormat: OFormat[OverseeingDTO] = Json.format[OverseeingDTO]

  def toOverseeingDTO(overseeing: Overseeing): OverseeingDTO =
    OverseeingDTO(overseeing.oversightId, overseeing.overseeAddress)

  def toOverseeing(overseeingDTO: OverseeingDTO): Overseeing =
    Overseeing(overseeingDTO.oversightId, overseeingDTO.overseeAddress)
}