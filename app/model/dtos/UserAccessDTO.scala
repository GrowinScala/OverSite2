package model.dtos

import play.api.libs.json.{ Json, OFormat }

case class UserAccessDTO(address: String, password: String, token: Option[String])

object UserAccessDTO {
  implicit val passwordFormat: OFormat[UserAccessDTO] = Json.format[UserAccessDTO]

  val test = UserAccessDTO("test", "test", None)

  def tupled = (UserAccessDTO.apply _).tupled

}