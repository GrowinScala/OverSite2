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

  def selectByAddressId(addressId: String): Query[AddressesTable, AddressesTable#TableElementType, scala.Seq] =
    all.filter(_.addressId === addressId)
  def selectByAddress(address: String): Query[AddressesTable, AddressesTable#TableElementType, scala.Seq] =
    all.filter(_.address === address)

  def selectAddressId(address: String): Query[Rep[String], String, scala.Seq] =
    selectByAddress(address).map(_.addressId)
  def selectAddress(addressId: String): Query[Rep[String], String, scala.Seq] =
    selectByAddressId(addressId).map(_.address)

}