package model.dtos

import play.api.libs.json.{ Json, OFormat }

case class EmailDTO(emailId: Int, from: String, to: Set[String], bcc: Set[String],
  cc: Set[String], body: String, date: String, sent: Boolean,
  attachments: Set[Int])

object EmailDTO {
  implicit val emailFormat: OFormat[EmailDTO] = Json.format[EmailDTO]

  def tupled = (EmailDTO.apply _).tupled

}