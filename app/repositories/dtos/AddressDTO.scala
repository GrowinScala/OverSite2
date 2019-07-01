package repositories.dtos

import play.api.libs.json.{Json, OFormat}
import repositories.mappings.Address

case class AddressDTO (addressId: Int, address: String) {
  def toAddress: Address = Address(this.addressId, this.address)
}

object AddressDTO {
  implicit val addressFormat : OFormat[AddressDTO] = Json.format[AddressDTO]

  def toAddressDTO(address: Address): AddressDTO = {
    AddressDTO(address.addressId, address.address)
  }
}
