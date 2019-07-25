package repositories.slick.mappings

import slick.jdbc.MySQLProfile.api._

case class OversightRow(oversightId: String, chatId: String, overseerId: String, overseeId: String)

class OversightsTable(tag: Tag) extends Table[OversightRow](tag, "oversights") {
  // Columns
  def oversightId = column[String]("oversight_id", O.PrimaryKey)
  def chatId = column[String]("chat_id")
  def overseerId = column[String]("overseer_id")
  def overseeId = column[String]("oversee_id")

  // Indexes

  // Table mapping
  override def * =
    (oversightId, chatId, overseerId, overseeId) <> (OversightRow.tupled, OversightRow.unapply)

}

object OversightsTable {
  val all = TableQuery[OversightsTable]

}