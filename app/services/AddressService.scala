package services

import javax.inject.Inject
import repositories.dtos.Address
import repositories.slick.implementations.AddressesRepository
import services.dtos.{AddressDTO, CreateAddressDTO}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class AddressService @Inject() (addresses: AddressesRepository) {

  def getAddress(id: Int): Future[Option[AddressDTO]] = {
    addresses.find(id) map {
      case Some(address : Address) => Some(AddressDTO.toAddressDTO(address))
      case None => None
    }
  }

  def postAddress(createAddressDTO: CreateAddressDTO) : Future[AddressDTO] = {
    val address = createAddressDTO.toAddressWithoutID
    addresses.insert(address).map{ id: Int => createAddressDTO.toAddressDTO(id) }
  }
	
	def deleteAddress(id: Int): Future[Boolean] = {
		addresses.delete(id)
	}
	
}
