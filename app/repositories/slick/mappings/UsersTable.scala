package repositories.slick.mappings

import repositories.dtos.User
import slick.jdbc.MySQLProfile.api._

trait UsersTable {

  class Users(tag: Tag) extends Table[User](tag, "users") {

    // Columns
    def userId = column[Int]("user_id", O.PrimaryKey)
    def addressId = column[Int]("address_id")
    def firstName = column[String]("first_name")
    def lastName = column[String]("last_name")

    // Indexes


    // Select
    override def * =
      (userId, addressId, firstName, lastName) <>(User.tupled, User.unapply)

  }

  val users = TableQuery[Users]

}