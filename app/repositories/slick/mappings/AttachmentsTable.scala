package repositories.slick.mappings

import slick.jdbc.MySQLProfile.api._

case class AttachmentRow(attachmentId: String, emailId: String, attachmentPath: String)

class AttachmentsTable(tag: Tag) extends Table[AttachmentRow](tag, "attachments") {
  // Columns
  def attachmentId = column[String]("attachment_id", O.PrimaryKey)
  def emailId = column[String]("email_id")
  def attachmentPath = column[String]("attachment_path")

  // Indexes

  // Table mapping
  override def * =
    (attachmentId, emailId, attachmentPath) <> (AttachmentRow.tupled, AttachmentRow.unapply)

}

object AttachmentsTable {
  val all = TableQuery[AttachmentsTable]

}