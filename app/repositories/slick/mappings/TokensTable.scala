package repositories.slick.mappings

import slick.jdbc.MySQLProfile.api._

case class TokenRow(tokenId: String, token: String, startDate: String, endDate: String)

class TokensTable(tag: Tag) extends Table[TokenRow](tag, "tokens") {
  // Columns
  def tokenId = column[String]("token_id", O.PrimaryKey)
  def token = column[String]("token")
  def startDate = column[String]("start_date")
  def endDate = column[String]("end_date")

  // Indexes

  // Table mapping
  override def * =
    (tokenId, token, startDate, endDate) <> (TokenRow.tupled, TokenRow.unapply)

}

object TokensTable {
  val all = TableQuery[TokensTable]
}