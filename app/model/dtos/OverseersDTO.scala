package model.dtos

import play.api.libs.json.{ Json, OFormat, OWrites, Reads }

case class OverseersDTO(user: String, overseers: Set[String])

object OverseersDTO {
  //implicit val overseersFormat: OFormat[OverseersDTO] = Json.format[OverseersDTO]
  //implicit val overseersReads: Reads[OverseersDTO] = Json.reads[OverseersDTO]
  implicit val overseersWrites: OWrites[OverseersDTO] = Json.writes[OverseersDTO]

  def tupled = (OverseersDTO.apply _).tupled

}