package repositories.slick.mappings

import model.types.ParticipantType
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType
import slick.jdbc.MySQLProfile.api._

case class EmailAddressRow(emailAddressId: String, emailId: String, chatId: String, addressId: String,
  participantType: ParticipantType)

class EmailAddressesTable(tag: Tag) extends Table[EmailAddressRow](tag, "email_addresses") {

  import EmailAddressesTable.participantColumnType

  // Columns
  def emailAddressId = column[String]("email_address_id", O.PrimaryKey)
  def emailId = column[String]("email_id")
  def chatId = column[String]("chat_id")
  def addressId = column[String]("address_id")
  def participantType = column[ParticipantType]("participant_type")

  // Indexes

  // Table mapping

  override def * =
    (emailAddressId, emailId, chatId, addressId, participantType) <> (EmailAddressRow.tupled, EmailAddressRow.unapply)

}

object EmailAddressesTable {
  val all = TableQuery[EmailAddressesTable]

  implicit val participantColumnType: JdbcType[ParticipantType] with BaseTypedType[ParticipantType] =
    MappedColumnType.base[ParticipantType, String](
      { participantType => participantType.value },
      { string => ParticipantType(string) })

  def selectByEmailIdAddressAndType(emailId: Option[String], addressId: Option[String],
    participantType: Option[ParticipantType]): Query[EmailAddressesTable, EmailAddressesTable#TableElementType, scala.Seq] =
    EmailAddressesTable.all
      .filterOpt(emailId)(_.emailId === _)
      .filterOpt(addressId)(_.addressId === _)
      .filterOpt(participantType)(_.participantType === _)

}