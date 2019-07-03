package services

import javax.inject.Inject
import model.dtos.AddressDTO
import repositories.AddressesRepository
import repositories.dtos.Address
import repositories.slick.implementations.SlickAddressesRepository

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


// Put the implementation here instead of the Trait because we're leaving injection for later
class AddressService @Inject() (addressesRep: SlickAddressesRepository) {

  def getAddress(id: Int): Future[Option[AddressDTO]] =
    addressesRep.find(id).map(_.map(row => AddressDTO(Some(row.addressId), row.address )))
	

  def postAddress(address: String) : Future[AddressDTO] = {
    addressesRep.insert(address).map(id => AddressDTO(Some(id), address))
  }
	
	def deleteAddress(id: Int): Future[Boolean] = {
		addressesRep.delete(id)
	}
	
}
