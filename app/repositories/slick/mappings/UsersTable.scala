package repositories.slick.mappings

import slick.jdbc.MySQLProfile.api._

case class UserRow(userId: String, addressId: String, firstName: String, lastName: String)

class UsersTable(tag: Tag) extends Table[UserRow](tag, "users") {
  // Columns
  def userId = column[String]("user_id", O.PrimaryKey)
  def addressId = column[String]("address_id")
  def firstName = column[String]("first_name")
  def lastName = column[String]("last_name")

  // Indexes

  // Table mapping
  override def * =
    (userId, addressId, firstName, lastName) <> (UserRow.tupled, UserRow.unapply)

}

object UsersTable {
  val all = TableQuery[UsersTable]

  def getUser(userId: String): Query[UsersTable, UsersTable#TableElementType, scala.Seq] =
    all.filter(_.userId === userId)

  def getUserAddressId(userId: String): Query[Rep[String], String, scala.Seq] =
    getUser(userId).map(_.addressId)

}