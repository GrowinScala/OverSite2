package model.dtos

import play.api.libs.json.{ Json, OFormat, OWrites, Reads }

case class CreateEmailDTO(emailId: Option[String], from: String, to: Option[Set[String]], bcc: Option[Set[String]],
  cc: Option[Set[String]], body: Option[String], date: String)

object CreateEmailDTO {
  implicit val createEmailFormat: OFormat[CreateEmailDTO] = Json.format[CreateEmailDTO]
  //implicit val createEmailReads: Reads[CreateEmailDTO] = Json.reads[CreateEmailDTO]
  //implicit val createEmailWrites: OWrites[CreateEmailDTO] = Json.writes[CreateEmailDTO]

  def tupled = (CreateEmailDTO.apply _).tupled

}

