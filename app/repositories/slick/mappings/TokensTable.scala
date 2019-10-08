package repositories.slick.mappings

import slick.jdbc.MySQLProfile.api._

case class TokenRow(tokenId: String, token: String)

class TokensTable(tag: Tag) extends Table[TokenRow](tag, "tokens") {
  // Columns
  def tokenId = column[String]("token_id", O.PrimaryKey)
  def token = column[String]("token")

  // Indexes

  // Table mapping
  override def * =
    (tokenId, token) <> (TokenRow.tupled, TokenRow.unapply)

}

object TokensTable {
  val all = TableQuery[TokensTable]
}