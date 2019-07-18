package repositories.slick.implementations

import javax.inject.Inject
import repositories.AddressesRepository
import repositories.dtos.Address
import repositories.slick.mappings.{ AddressRow, AddressesTable }
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ ExecutionContext, Future }

class SlickAddressesRepository @Inject() (db: Database)(implicit executionContext: ExecutionContext)
  extends AddressesRepository {

/******* Queries here **********/

  def insert(address: String): Future[Int] = db.run(AddressesTable.all returning AddressesTable.all.map(_.addressId) +=
    AddressRow(0, address))

  def find(id: Int): Future[Option[Address]] = db.run(AddressesTable.all.filter(_.addressId === id).result.headOption)
    .map(_.map(row => Address(row.addressId, row.address)))

  def update(id: Int, address: String): Future[Boolean] = {
    val query = for (row <- AddressesTable.all if row.addressId === id)
      yield row.address
    db.run(query.update(address)) map { _ > 0 }
  }

  def delete(id: Int): Future[Boolean] =
    db.run(AddressesTable.all.filter(_.addressId === id).delete) map { _ > 0 }

  /* def getNames(id: Int) = db.run(
    sql"select address from addresses where address_id = #$id"
      .as[String].headOption)*/

}
