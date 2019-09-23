package repositories.dtos

case class UpsertEmail(emailId: Option[String], from: Option[String], to: Option[Set[String]], bcc: Option[Set[String]],
  cc: Option[Set[String]], body: Option[String], date: Option[String], sent: Option[Boolean])
