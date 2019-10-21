package repositories.slick.mappings

import slick.jdbc.MySQLProfile.api._

case class EmailRow(emailId: String, chatId: String, body: String, date: String, sent: Int)

class EmailsTable(tag: Tag) extends Table[EmailRow](tag, "emails") {
  // Columns
  def emailId = column[String]("email_id", O.PrimaryKey)
  def chatId = column[String]("chat_id")
  def body = column[String]("body")
  def date = column[String]("date")
  def sent = column[Int]("sent")

  // Indexes

  // Table mapping
  override def * =
    (emailId, chatId, body, date, sent) <> (EmailRow.tupled, EmailRow.unapply)

}

object EmailsTable {
  val all = TableQuery[EmailsTable]
  //val insert = (all.returning(all.map(_.emailId)))

  def getChatEmails(chatId: String): Query[EmailsTable, EmailsTable#TableElementType, scala.Seq] =
    all.filter(email => email.chatId === chatId)

  def getChatEmailsIds(chatId: String): Query[Rep[String], String, scala.Seq] =
    getChatEmails(chatId).map(email => email.emailId)

}