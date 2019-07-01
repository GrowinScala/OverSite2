package repositories.slick.mappings
import repositories.dtos.Attachment
import slick.jdbc.MySQLProfile.api._

trait AttachmentsTable {
	class Attachments(tag: Tag) extends Table[Attachment](tag, "attachments") {
		// Columns
		def attachmentId = column[Int]("attachment_id", O.PrimaryKey, O.AutoInc)
		def emailId = column[Int]("email_id")
		
		// Indexes
		
		
		// Select
		override def * =
			(attachmentId, emailId) <> (Attachment.tupled, Attachment.unapply)
		
	}
	
	val attachments = TableQuery[Attachments]
	
	
}
