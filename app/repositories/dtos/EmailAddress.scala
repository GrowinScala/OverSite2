package repositories.dtos
//import repositories.dtos.receiverType.receiverType

/* We use a default id so that we can ommit the id on the '+=' method.
We use a negative number because we would get an error if this default value
were to be accepted*/


case class EmailAddress (emailAddressId : Int, emailId : Int, addressId: Int, receiverType: String)

/*
object receiverType extends Enumeration {
	type receiverType = Value
	val TO, CC, BCC = Value
}
*/
