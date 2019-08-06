package model.dtos

import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

case class CreateEmailDTO(emailId: Option[String], from: String, to: Option[Set[String]], bcc: Option[Set[String]],
  cc: Option[Set[String]], body: Option[String], date: Option[String])

object CreateEmailDTO {
  implicit val createEmailWrites: OWrites[CreateEmailDTO] = Json.writes[CreateEmailDTO]

  implicit val createEmailDTOReads: Reads[CreateEmailDTO] = (
    (JsPath \ "emailId").readNullable[String] and
    (JsPath \ "from").read[String](email) and
    (JsPath \ "to").readNullable[Set[String]](Reads.set(email)) and
    (JsPath \ "bcc").readNullable[Set[String]](Reads.set(email)) and
    (JsPath \ "cc").readNullable[Set[String]](Reads.set(email)) and
    (JsPath \ "body").readNullable[String] and
    (JsPath \ "date").readNullable[String])(CreateEmailDTO.apply _)

  def tupled = (CreateEmailDTO.apply _).tupled

}