package repositories.slick.mappings
import repositories.dtos.EmailAddress
import slick.jdbc.MySQLProfile.api._

trait EmailAddressesTable {
	class EmailAddresses(tag: Tag) extends Table[EmailAddress](tag, "email_addresses") {
		// Columns
		def emailAddressId = column[Int]("email_address_id", O.PrimaryKey, O.AutoInc)
		def emailId = column[Int]("email_id")
		def addressId = column[Int]("address_id")
		def receiverType = column[String]("receiver_type")
		
		
		
		override def * =
			(emailAddressId , emailId , addressId, receiverType) <> (EmailAddress.tupled, EmailAddress.unapply)
		
	}
	
	val emailAddresses = TableQuery[EmailAddresses]
	
}
