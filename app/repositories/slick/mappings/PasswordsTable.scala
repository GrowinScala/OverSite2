package repositories.slick.mappings

import slick.jdbc.MySQLProfile.api._

case class PasswordsRow(passwordId: String, userId: String, password: String, tokenId: String)

class PasswordsTable(tag: Tag) extends Table[PasswordsRow](tag, "passwords") {
  // Columns
  def passwordId = column[String]("password_id", O.PrimaryKey)
  def userId = column[String]("user_id")
  def password = column[String]("password")
  def tokenId = column[String]("token_id")

  // Indexes

  // Table mapping
  override def * =
    (passwordId, userId, password, tokenId) <> (PasswordsRow.tupled, PasswordsRow.unapply)

}

object PasswordsTable {
  val all = TableQuery[PasswordsTable]

}