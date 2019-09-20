package model.dtos

import play.api.libs.json.Reads.email
import play.api.libs.json._
import play.api.libs.functional.syntax._

case class UserAccessDTO(address: String, password: String,
  first_name: Option[String], last_name: Option[String], token: Option[String])

object UserAccessDTO {
  //implicit val userAccessDTOFormat: OFormat[UserAccessDTO] = Json.format[UserAccessDTO]

  implicit val userAccessDTOWrites: OWrites[UserAccessDTO] = Json.writes[UserAccessDTO]

  implicit val userAccessDTOReads: Reads[UserAccessDTO] = (
    (JsPath \ "address").read[String](email) and
    (JsPath \ "password").read[String] and
    (JsPath \ "first_name").readNullable[String] and
    (JsPath \ "last_name").readNullable[String] and
    (JsPath \ "token").readNullable[String])(UserAccessDTO.apply _)

  def tupled = (UserAccessDTO.apply _).tupled

}