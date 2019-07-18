package repositories.dtos

case class Email(emailId: Int, from: String, to: Seq[String], bcc: Seq[String],
  cc: Seq[String], body: String, date: String, sent: Int, attachments: Seq[Int])

