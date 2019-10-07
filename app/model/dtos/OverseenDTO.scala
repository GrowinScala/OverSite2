package model.dtos

import play.api.libs.json.{ Json, OFormat }
import repositories.dtos.Overseen

case class OverseenDTO(oversightId: String, overseerAddress: String)

object OverseenDTO {
  implicit val OverseenDtoOFormat: OFormat[OverseenDTO] = Json.format[OverseenDTO]

  def toOverseenDTO(overseen: Overseen): OverseenDTO =
    OverseenDTO(overseen.oversightId, overseen.overseerAddress)

  def toOverseen(overseenDTO: OverseenDTO): Overseen =
    Overseen(overseenDTO.oversightId, overseenDTO.overseerAddress)
}
