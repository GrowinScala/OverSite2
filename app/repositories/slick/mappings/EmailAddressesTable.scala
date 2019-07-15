package repositories.slick.mappings

import slick.jdbc.MySQLProfile.api._

case class EmailAddressRow(emailAddressId: Int, emailId: Int, addressId: Int, participantType: String)

class EmailAddressesTable(tag: Tag) extends Table[EmailAddressRow](tag, "email_addresses") {
  // Columns
  def emailAddressId = column[Int]("email_address_id", O.PrimaryKey, O.AutoInc)
  def emailId = column[Int]("email_id")
  def addressId = column[Int]("address_id")
  def participantType = column[String]("participant_type")

  // Indexes

  // Table mapping
  override def * =
    (emailAddressId, emailId, addressId, participantType) <> (EmailAddressRow.tupled, EmailAddressRow.unapply)

}

object EmailAddressesTable {
  val all = TableQuery[EmailAddressesTable]

}