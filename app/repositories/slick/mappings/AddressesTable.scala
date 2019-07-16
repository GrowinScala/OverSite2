package repositories.slick.mappings

import slick.jdbc.MySQLProfile.api._

case class AddressRow(addressId: Int, address: String)

class AddressesTable(tag: Tag) extends Table[AddressRow](tag, "addresses") {
  // Columns
  def addressId = column[Int]("address_id", O.PrimaryKey, O.AutoInc)
  def address = column[String]("address")

  // Indexes

  // Table mapping
  override def * =
    (addressId, address) <> (AddressRow.tupled, AddressRow.unapply)

}

object AddressesTable {
  val all = TableQuery[AddressesTable]

}