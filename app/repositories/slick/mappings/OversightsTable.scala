package repositories.slick.mappings


import slick.jdbc.MySQLProfile.api._

case class OversightRow (oversightId: Int, userChatId: Int, userId: Int)

class OversightsTable (tag: Tag) extends Table[OversightRow](tag, "oversights") {
  // Columns
  def oversightId = column[Int]("oversight_id", O.PrimaryKey, O.AutoInc)
  def userChatId = column[Int]("user_chat_id")
  def userId = column[Int]("user_id")
  
  // Indexes
  
  
  // Table mapping
  override def * =
    (oversightId, userChatId, userId) <> (OversightRow.tupled, OversightRow.unapply)
  
}

object OversightsTable {
  val all = TableQuery[OversightsTable]
  
}