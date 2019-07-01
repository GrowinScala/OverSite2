package repositories.slick.mappings

import repositories.dtos.Oversight
import slick.jdbc.MySQLProfile.api._

trait OversightsTable {

  class Oversights(tag: Tag) extends Table[Oversight](tag, "oversights") {

    // Columns
    def oversightId = column[String]("oversight_id", O.PrimaryKey)
    def userChatId = column[String]("user_chat_id")
    def userId = column[String]("user_id")

    // Indexes


    // Select
    override def * =
      (oversightId, userChatId, userId) <>(Oversight.tupled, Oversight.unapply)

  }

  val users = TableQuery[Oversights]

}
