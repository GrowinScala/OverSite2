package repositories.dtos

case class UpsertEmail(emailId: Option[String], from: Option[String], to: Option[Set[String]], bcc: Option[Set[String]],
  cc: Option[Set[String]], body: Option[String], date: Option[String], sent: Option[Boolean])

object UpsertEmail {
  def fromUpsertEmailToEmail(upsertEmail: UpsertEmail): Email = {
    Email(
      emailId = upsertEmail.emailId.getOrElse(""),
      from = upsertEmail.from.getOrElse(""),
      to = upsertEmail.to.getOrElse(Set()),
      bcc = upsertEmail.bcc.getOrElse(Set()),
      cc = upsertEmail.cc.getOrElse(Set()),
      body = upsertEmail.body.getOrElse(""),
      date = upsertEmail.date.getOrElse(""),
      sent = if (upsertEmail.sent.getOrElse(false)) 1 else 0,
      attachments = Set())
  }
}
