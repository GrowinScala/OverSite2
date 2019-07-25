package model.dtos

import play.api.libs.json.{ Json, OFormat }

case class EmailDTO(emailId: String, from: String, to: Seq[String], bcc: Seq[String],
  cc: Seq[String], body: String, date: String, sent: Boolean,
  attachments: Seq[String])

object EmailDTO {
  implicit val emailFormat: OFormat[EmailDTO] = Json.format[EmailDTO]

  def tupled = (EmailDTO.apply _).tupled

}