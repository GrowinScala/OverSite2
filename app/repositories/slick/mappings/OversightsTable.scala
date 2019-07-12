package repositories.slick.mappings

import slick.jdbc.MySQLProfile.api._

case class OversightRow(oversightId: Int, chatId: Int, overseerId: Int, overseeId: Int)

class OversightsTable(tag: Tag) extends Table[OversightRow](tag, "oversights") {
  // Columns
  def oversightId = column[Int]("oversight_id", O.PrimaryKey, O.AutoInc)
  def chatId = column[Int]("chat_id")
  def overseerId = column[Int]("overseer_id")
  def overseeId = column[Int]("oversee_id")

  // Indexes

  // Table mapping
  override def * =
    (oversightId, chatId, overseerId, overseeId) <> (OversightRow.tupled, OversightRow.unapply)

}

object OversightsTable {
  val all = TableQuery[OversightsTable]

}