package repositories.dtos

import model.dtos.AddressDTO
import play.api.libs.json.{ Json, OFormat }

case class Address(addressId: Int, address: String)

