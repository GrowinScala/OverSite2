package repositories.slick.mappings

import slick.jdbc.MySQLProfile.api._

case class AttachmentRow(attachmentId: String, emailId: String)

class AttachmentsTable(tag: Tag) extends Table[AttachmentRow](tag, "attachments") {
  // Columns
  def attachmentId = column[String]("attachment_id", O.PrimaryKey)
  def emailId = column[String]("email_id")

  // Indexes

  // Table mapping
  override def * =
    (attachmentId, emailId) <> (AttachmentRow.tupled, AttachmentRow.unapply)

}

object AttachmentsTable {
  val all = TableQuery[AttachmentsTable]

}