package repositories.dtos

import play.api.libs.json.{Json, OFormat}
import repositories.mappings.Address

case class CreateAddressDTO (address: String) {

  def toAddress: Address =
    Address(address = this.address)

  def toAddressDTO(id: Int): AddressDTO =
    AddressDTO(id, this.address)
}

object CreateAddressDTO {
  implicit val addressFormat : OFormat[CreateAddressDTO] = Json.format[CreateAddressDTO]

}