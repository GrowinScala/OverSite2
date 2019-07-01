package repositories.slick.mappings
import repositories.dtos.Email
import slick.jdbc.MySQLProfile.api._

trait EmailsTable {
	class Emails(tag: Tag) extends Table[Email](tag, "emails") {
		// Columns
		def emailId = column[Int]("email_id", O.PrimaryKey, O.AutoInc)
		def fromAddressId = column[Int]("from_address_id")
		def chatId = column[Int]("chat_id")
		def body = column[String]("body")
		def date = column[String]("date")
		def sent = column[Int]("sent")
		
		
		
		override def * =
			(emailId, fromAddressId, chatId, body, date, sent) <> (Email.tupled, Email.unapply)
		
	}
	
	val emails = TableQuery[Emails]
	
}
