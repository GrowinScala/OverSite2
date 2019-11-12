package model.dtos

import play.api.libs.json.{ Json, OFormat, OWrites }
import repositories.dtos.Email

case class EmailDTO(emailId: String, from: String, to: Set[String], bcc: Set[String],
  cc: Set[String], body: String, date: String, sent: Boolean,
  attachments: Set[String])

object EmailDTO {
  implicit val emailWrites: OWrites[EmailDTO] = Json.writes[EmailDTO]

  def tupled = (EmailDTO.apply _).tupled

  def toEmailDTO(optionEmail: Option[Email]): Option[EmailDTO] = {
    optionEmail.map {
      email =>
        EmailDTO(
          email.emailId,
          email.from,
          email.to,
          email.bcc,
          email.cc,
          email.body,
          email.date,
          email.sent != 0,
          email.attachments)
    }
  }

  def toEmailDTO(email: Email): EmailDTO =
    EmailDTO(
      email.emailId,
      email.from,
      email.to,
      email.bcc,
      email.cc,
      email.body,
      email.date,
      email.sent != 0,
      email.attachments)

}