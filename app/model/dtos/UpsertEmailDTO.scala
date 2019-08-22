package model.dtos

import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

case class UpsertEmailDTO(emailId: Option[String], from: Option[String], to: Option[Set[String]], bcc: Option[Set[String]],
  cc: Option[Set[String]], body: Option[String], date: Option[String], sent: Option[Boolean])

object UpsertEmailDTO {
  implicit val createEmailWrites: OWrites[UpsertEmailDTO] = Json.writes[UpsertEmailDTO]

  implicit val createEmailDTOReads: Reads[UpsertEmailDTO] = (
    (JsPath \ "emailId").readNullable[String] and
    (JsPath \ "from").readNullable[String](email) and
    (JsPath \ "to").readNullable[Set[String]](Reads.set(email)) and
    (JsPath \ "bcc").readNullable[Set[String]](Reads.set(email)) and
    (JsPath \ "cc").readNullable[Set[String]](Reads.set(email)) and
    (JsPath \ "body").readNullable[String] and
    (JsPath \ "date").readNullable[String] and
    (JsPath \ "sent").readNullable[Boolean])(UpsertEmailDTO.apply _)

  def tupled = (UpsertEmailDTO.apply _).tupled

}