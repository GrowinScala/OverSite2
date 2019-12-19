package repositories.slick.mappings

import slick.jdbc.MySQLProfile.api._

case class AttachmentRow(attachmentId: String, emailId: String, filename: String, path: String, contentType: Option[String])

class AttachmentsTable(tag: Tag) extends Table[AttachmentRow](tag, "attachments") {
  // Columns
  def attachmentId = column[String]("attachment_id", O.PrimaryKey)
  def emailId = column[String]("email_id")
  def filename = column[String]("filename")
  def path = column[String]("path")
  def contentType = column[Option[String]]("content_type")

  // Indexes

  // Table mapping
  override def * =
    (attachmentId, emailId, filename, path, contentType) <> (AttachmentRow.tupled, AttachmentRow.unapply)

}

object AttachmentsTable {
  val all = TableQuery[AttachmentsTable]

}