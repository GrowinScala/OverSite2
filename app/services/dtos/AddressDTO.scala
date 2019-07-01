package services.dtos

import play.api.libs.json.{Json, OFormat}

case class AddressDTO (addressId: Int = -1, address: String) {
  def toAddress: AddressDTO = AddressDTO(this.addressId, this.address)
}

object AddressDTO {
  implicit val addressFormat : OFormat[AddressDTO] = Json.format[AddressDTO]

  def toAddressDTO(address: AddressDTO): AddressDTO = {
    AddressDTO(address.addressId, address.address)
  }
  
  def tupled = (AddressDTO.apply _).tupled
  
}
