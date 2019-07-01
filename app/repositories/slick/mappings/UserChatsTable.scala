package repositories.slick.mappings

import repositories.dtos.UserChat
import slick.jdbc.MySQLProfile.api._

trait UserChatsTable {

  class UserChats(tag: Tag) extends Table[UserChat](tag, "user_chats") {

    // Columns
    def userChatId = column[Int]("user_chat_id", O.PrimaryKey, O.AutoInc)
    def userId = column[Int]("user_id")
    def chatId = column[Int]("chat_id")
    def mailBox = column[String]("mailbox")

    // Indexes


    // Select
    override def * =
      (userChatId, userId, chatId, mailBox) <>(UserChat.tupled, UserChat.unapply)

  }

  val userChats = TableQuery[UserChats]

}
