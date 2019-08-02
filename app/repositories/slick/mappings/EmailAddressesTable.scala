package repositories.slick.mappings

import slick.jdbc.MySQLProfile.api._

case class EmailAddressRow(emailAddressId: String, emailId: String, chatId: String, addressId: String, participantType: String)

class EmailAddressesTable(tag: Tag) extends Table[EmailAddressRow](tag, "email_addresses") {
  // Columns
  def emailAddressId = column[String]("email_address_id", O.PrimaryKey)
  def emailId = column[String]("email_id")
  def chatId = column[String]("chat_id")
  def addressId = column[String]("address_id")
  def participantType = column[String]("participant_type")

  // Indexes

  // Table mapping
  override def * =
    (emailAddressId, emailId, chatId, addressId, participantType) <> (EmailAddressRow.tupled, EmailAddressRow.unapply)

}

object EmailAddressesTable {
  val all = TableQuery[EmailAddressesTable]

  def selectByEmailIdAddressAndType(emailId: String, addressId: String, participantType: String): Query[EmailAddressesTable, EmailAddressesTable#TableElementType, scala.Seq] =
    EmailAddressesTable.all
      .filter(ea => ea.emailId === emailId && ea.addressId === addressId && ea.participantType === participantType)

}