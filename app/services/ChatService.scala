package services


import javax.inject.Inject
import model.dtos.AddressDTO
import repositories.AddressesRepository
import repositories.dtos.Address
import repositories.slick.implementations.SlickAddressesRepository

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


// Put the implementation here instead of the Trait because we're leaving injection for later
class ChatService @Inject() (addressesRep: SlickAddressesRepository) {


}
