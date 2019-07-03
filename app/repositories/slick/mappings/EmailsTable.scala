package repositories.slick.mappings


import slick.jdbc.MySQLProfile.api._

case class EmailRow (emailId: Int, fromAddressId: Int, chatId: Int, body: String, date: String, sent: Int)

class EmailsTable (tag: Tag) extends Table[EmailRow](tag, "emails") {
	// Columns
	def emailId = column[Int]("email_id", O.PrimaryKey, O.AutoInc)
	def fromAddressId = column[Int]("from_address_id")
	def chatId = column[Int]("chat_id")
	def body = column[String]("body")
	def date = column[String]("date")
	def sent = column[Int]("sent")
	
	// Indexes
	
	
	// Table mapping
	override def * =
		(emailId, fromAddressId, chatId, body, date, sent) <> (EmailRow.tupled, EmailRow.unapply)
	
}

object EmailsTable {
	val all = TableQuery[EmailsTable]
	
}