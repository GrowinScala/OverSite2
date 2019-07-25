package repositories.slick.mappings

import slick.jdbc.MySQLProfile.api._

case class AddressRow(addressId: String, address: String)

class AddressesTable(tag: Tag) extends Table[AddressRow](tag, "addresses") {
  // Columns
  def addressId = column[String]("address_id", O.PrimaryKey)
  def address = column[String]("address")

  // Indexes

  // Table mapping
  override def * =
    (addressId, address) <> (AddressRow.tupled, AddressRow.unapply)

}

object AddressesTable {
  val all = TableQuery[AddressesTable]

}