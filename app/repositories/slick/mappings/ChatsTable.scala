package repositories.slick.mappings

import repositories.dtos.Chat
import slick.jdbc.MySQLProfile.api._

trait ChatsTable {

  class Chats(tag: Tag) extends Table[Chat](tag, "chats") {
    // Columns
    def chatId = column[Int]("chat_id", O.PrimaryKey, O.AutoInc)
    def subject = column[String]("subject")

    // Indexes


    // Select
    override def * =
      (chatId, subject) <>(Chat.tupled, Chat.unapply)

  }

  val chats = TableQuery[Chats]

}