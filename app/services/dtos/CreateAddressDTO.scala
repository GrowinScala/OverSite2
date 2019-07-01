package services.dtos

import play.api.libs.json.{Json, OFormat}
import repositories.dtos.Address

case class CreateAddressDTO (address: String) {

  def toAddressWithoutID: Address =
    Address(address = this.address)

  def toAddressDTO(id: Int): AddressDTO =
    AddressDTO(id, this.address)
}

object CreateAddressDTO {
  implicit val addressFormat : OFormat[CreateAddressDTO] = Json.format[CreateAddressDTO]

}