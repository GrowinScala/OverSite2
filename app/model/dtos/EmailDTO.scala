package model.dtos

import play.api.libs.json.{Json, OFormat}


case class EmailDTO (emailId: Int, from: String, to: Array[String], bcc: Array[String],
                     cc: Array[String], body: String, date: String, sent: Boolean)

//TODO Attachments missing

object EmailDTO {
  implicit val emailFormat : OFormat[EmailDTO] = Json.format[EmailDTO]

  def tupled = (EmailDTO.apply _).tupled

}