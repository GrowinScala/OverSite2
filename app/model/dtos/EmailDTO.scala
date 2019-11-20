package model.dtos

import controllers.AuthenticatedUser
import play.api.libs.json.{ Json, OFormat, OWrites }
import repositories.dtos.Email
import controllers.ChatController._
import play.api.mvc.AnyContent

case class EmailDTO(emailId: String, emailLink: String, from: String, to: Set[String], bcc: Set[String],
  cc: Set[String], body: String, date: String, sent: Boolean,
  attachments: Set[String])

object EmailDTO {
  implicit val emailWrites: OWrites[EmailDTO] = Json.writes[EmailDTO]

  def tupled = (EmailDTO.apply _).tupled

  def toEmailDTO(chatId: String, optionEmail: Option[Email], auth: AuthenticatedUser[Any]): Option[EmailDTO] = {
    optionEmail.map {
      email =>
        EmailDTO(
          email.emailId,
          makeGetEmailLink(chatId, email.emailId, auth),
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
}