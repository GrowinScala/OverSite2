package repositories.slick.mappings


import slick.jdbc.MySQLProfile.api._

case class AttachmentRow (attachmentId: Int, emailId: Int)

class AttachmentsTable (tag: Tag) extends Table[AttachmentRow](tag, "attachments") {
	// Columns
	def attachmentId = column[Int]("attachment_id", O.PrimaryKey, O.AutoInc)
	def emailId = column[Int]("email_id")
	
	// Indexes
	
	
	// Table mapping
	override def * =
		(attachmentId, emailId) <> (AttachmentRow.tupled, AttachmentRow.unapply)
	
}

object AttachmentsTable {
	val all = TableQuery[AttachmentsTable]
	
}