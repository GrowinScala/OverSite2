package services

import javax.inject.Inject
import repositories.AddressesRepository
import repositories.dtos.{AddressDTO, CreateAddressDTO}
import repositories.mappings.Address

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class AddressService @Inject() (addresses: AddressesRepository) {

  def getAddress(id: Int): Future[Option[AddressDTO]] = {
    addresses.find(id) map {
      case Some(address : Address) => Some(AddressDTO(address.addressId, address.address))
      case None => None
    }
  }

  def postAddress(createAddressDTO: CreateAddressDTO) : Future[AddressDTO] = {
    val address = createAddressDTO.toAddress
    addresses.insert(address).map{ id: Int => createAddressDTO.toAddressDTO(id) }
  }
}
