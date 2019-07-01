package repositories.slick.mappings

import repositories.dtos.UserChat
import slick.jdbc.MySQLProfile.api._

trait UserChatsTable {

  class UserChats(tag: Tag) extends Table[UserChat](tag, "user_chats") {

    // Columns
    def userChatId = column[String]("user_chat_id", O.PrimaryKey)
    def userId = column[Int]("user_id")
    def chatId = column[Int]("chat_id")
    def mailBox = column[String]("mail_box")

    // Indexes


    // Select
    override def * =
      (userChatId, userId, chatId, mailBox) <>(UserChat.tupled, UserChat.unapply)

  }

  val userChats = TableQuery[UserChats]

}
