package repositories

import com.google.inject.Inject
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

import slick.jdbc.MySQLProfile.api._


case class Address(addressId: Long, address: String)

class AddressesTable(tag: Tag) extends Table[Address](tag, "addresses") {
  def addressId = column[Long]("address_id", O.PrimaryKey, O.AutoInc)
  def address = column[String]("address")

  override def * =
    (addressId, address) <>(Address.tupled, Address.unapply)
}

class Addresses @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {

  val addresses = TableQuery[AddressesTable]

  // Get address by ID
  def get(addressId: Long): Future[Option[Address]] = {
    dbConfig.db.run(addresses.filter(_.addressId === addressId).result.headOption)
  }
}
