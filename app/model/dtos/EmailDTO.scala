package model.dtos

import play.api.libs.json.{ Json, OFormat, OWrites }

case class EmailDTO(emailId: String, from: String, to: Set[String], bcc: Set[String],
  cc: Set[String], body: String, date: String, sent: Boolean,
  attachments: Set[String])

object EmailDTO {
  //implicit val emailFormat: OFormat[EmailDTO] = Json.format[EmailDTO]
  //implicit val emailReads: Reads[EmailDTO] = Json.reads[EmailDTO]
  implicit val emailWrites: OWrites[EmailDTO] = Json.writes[EmailDTO]

  def tupled = (EmailDTO.apply _).tupled

}