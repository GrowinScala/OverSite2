package repositories.mappings

import slick.jdbc.MySQLProfile.api._

trait AddressesTable {

  class Addresses(tag: Tag) extends Table[Address](tag, "addresses") {
    // Columns
    def addressId = column[Int]("address_id", O.PrimaryKey, O.AutoInc)
    def address = column[String]("address")

    // Indexes


    // Select
    override def * =
    (addressId, address) <>(Address.tupled, Address.unapply)

  }

   val addresses = TableQuery[Addresses]

}
