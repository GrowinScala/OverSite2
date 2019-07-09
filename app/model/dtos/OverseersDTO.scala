package model.dtos

import play.api.libs.json.{Json, OFormat}

case class OverseersDTO (user: String, overseers: Array[String])

object OverseersDTO {
  implicit val overseersFormat : OFormat[OverseersDTO] = Json.format[OverseersDTO]

  def tupled = (OverseersDTO.apply _).tupled

}