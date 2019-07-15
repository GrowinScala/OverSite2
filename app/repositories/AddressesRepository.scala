package repositories

import com.google.inject.ImplementedBy
import repositories.dtos.Address
import repositories.slick.implementations.SlickAddressesRepository

import scala.concurrent.Future

trait AddressesRepository {

  def insert(address: String): Future[Int]

  def find(id: Int): Future[Option[Address]]

  def update(id: Int, address: String): Future[Boolean]

  def delete(id: Int): Future[Boolean]

}
