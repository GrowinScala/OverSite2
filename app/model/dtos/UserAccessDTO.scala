package model.dtos

import play.api.libs.json.Reads.email
import play.api.libs.json._
import play.api.libs.functional.syntax._
import repositories.dtos.UserAccess

case class UserAccessDTO(address: String, password: String,
  first_name: Option[String], last_name: Option[String], token: Option[String])

object UserAccessDTO {
  implicit val userAccessDTOWrites: OWrites[UserAccessDTO] = Json.writes[UserAccessDTO]

  implicit val userAccessDTOReads: Reads[UserAccessDTO] = (
    (JsPath \ "address").read[String](email) and
    (JsPath \ "password").read[String] and
    (JsPath \ "first_name").readNullable[String] and
    (JsPath \ "last_name").readNullable[String] and
    (JsPath \ "token").readNullable[String])(UserAccessDTO.apply _)

  def tupled = (UserAccessDTO.apply _).tupled

  def toUserAccessDTO(userAccess: UserAccess): UserAccessDTO = {
    UserAccessDTO(
      address = userAccess.address,
      password = userAccess.password,
      first_name = userAccess.first_name,
      last_name = userAccess.last_name,
      token = userAccess.token)
  }

  def toUserAccess(userAccessDTO: UserAccessDTO): UserAccess = {
    UserAccess(
      address = userAccessDTO.address,
      password = userAccessDTO.password,
      first_name = userAccessDTO.first_name,
      last_name = userAccessDTO.last_name,
      token = userAccessDTO.token)
  }

}