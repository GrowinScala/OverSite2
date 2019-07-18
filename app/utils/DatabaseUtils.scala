package utils
import slick.jdbc.MySQLProfile.backend.Database

object DatabaseUtils {

  val DEFAULT_DB = Database.forConfig("dbinfo")

}
