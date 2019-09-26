package model.dtos

import play.api.libs.json.{ Json, OFormat }

case class OverseersDTO(overseeAddress: String, overseersAddresses: Set[String])

object OverseersDTO {
  implicit val overseersFormat: OFormat[OverseersDTO] = Json.format[OverseersDTO]

  def tupled = (OverseersDTO.apply _).tupled

}