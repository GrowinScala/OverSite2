package repositories.dtos

import play.api.libs.json.{Json, OFormat}

case class CreateAddressDTO (address: String) {

  def toAddressDTOWithoutID: AddressDTO =
    AddressDTO(address = this.address)

  def toAddressDTO(id: Int): AddressDTO =
    AddressDTO(id, this.address)
}

object CreateAddressDTO {
  implicit val addressFormat : OFormat[CreateAddressDTO] = Json.format[CreateAddressDTO]

}