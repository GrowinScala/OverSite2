package repositories.mappings

import slick.jdbc.MySQLProfile.api._
import repositories.dtos.AddressDTO

trait AddressesTable {

  class Addresses(tag: Tag) extends Table[AddressDTO](tag, "addresses") {
    // Columns
    def addressId = column[Int]("address_id", O.PrimaryKey, O.AutoInc)
    def address = column[String]("address")

    // Indexes


    // Select
    override def * =
    (addressId, address) <>(AddressDTO.tupled, AddressDTO.unapply)

  }

   val addresses = TableQuery[Addresses]

}
