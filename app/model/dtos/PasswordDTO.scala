package model.dtos

import play.api.libs.json.{ Json, OFormat }

case class PasswordDTO(address: String, password: String)

object PasswordDTO {
	implicit val passwordFormat: OFormat[PasswordDTO] = Json.format[PasswordDTO]
	
	def tupled = (PasswordDTO.apply _).tupled
	
}