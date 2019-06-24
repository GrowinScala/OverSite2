package services

import com.google.inject.Inject
import repositories.{Address, Addresses}

import scala.concurrent.Future

class AddressService @Inject() (addresses: Addresses) {

  def getAddress(id: Long): Future[Option[Address]] = {
    addresses.get(id)
  }
}
