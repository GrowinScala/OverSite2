package repositories.dtos

case class Email(emailId: Int, from: String, to: Set[String], bcc: Set[String],
  cc: Set[String], body: String, date: String, sent: Int, attachments: Set[Int])

