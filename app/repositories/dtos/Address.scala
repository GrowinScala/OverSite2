package repositories.dtos

import play.api.libs.json.{Json, OFormat}
import services.dtos.AddressDTO

/* We use a default id so that we can ommit the id on the '+=' method.
We use a negative number because we would get an error if this default value
were to be accepted*/

case class Address (addressId: Int = -1, address: String) {
	def toAddress: Address = Address(this.addressId, this.address)
}

object Address {
	implicit val addressFormat : OFormat[Address] = Json.format[Address]
	
	def toAddress(addressDTO: AddressDTO): Address = {
		Address(addressDTO.addressId, addressDTO.address)
	}
	
	def tupled = (Address.apply _).tupled
	
}



	

