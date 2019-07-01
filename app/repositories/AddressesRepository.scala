package repositories

import javax.inject.Inject
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.db.NamedDatabase
import slick.dbio.DBIOAction
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}
import repositories.mappings.AddressesTable
import repositories.dtos.AddressDTO


class AddressesRepository @Inject()
    (@NamedDatabase("example") protected val dbConfigProvider: DatabaseConfigProvider)
    (implicit executionContext: ExecutionContext)
  extends AddressesTable with HasDatabaseConfigProvider[JdbcProfile] {

  /******* Queries here **********/

  def init() = db.run(DBIOAction.seq(addresses.schema.create))
  def drop() = db.run(DBIOAction.seq(addresses.schema.drop))

  def insert(address: AddressDTO): Future[Int] = db.run(addresses returning addresses.map(_.addressId) += address)

  def find(id: Int) = db.run(addresses.filter(_.addressId === id).result.headOption)

  def update(id: Int, address: String) = {
    val query = for (address <- addresses if address.addressId === id)
      yield address.address
    db.run(query.update(address)) map { _ > 0 }
  }

  def delete(id: Int) =
    db.run(addresses.filter(_.addressId === id).delete) map { _ > 0 }

  def getNames(id: Int) = db.run(
    sql"select address from addresses where address_id = #$id"
      .as[String].headOption)
}
