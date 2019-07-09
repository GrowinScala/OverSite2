package repositories.slick.mappings


import slick.jdbc.MySQLProfile.api._

case class UserChatRow (userChatId: Int, userId: Int, chatId: Int, mailBox: String)

class UserChatsTable (tag: Tag) extends Table[UserChatRow](tag, "user_chats") {
  // Columns
  def userChatId = column[Int]("user_chat_id", O.PrimaryKey, O.AutoInc)
  def userId = column[Int]("user_id")
  def chatId = column[Int]("chat_id")
  def mailBox = column[String]("mail_box")
  
  // Indexes
  
  
  // Table mapping
  override def * =
    (userChatId, userId, chatId, mailBox) <> (UserChatRow.tupled, UserChatRow.unapply)
  
}

object UserChatsTable {
  val all = TableQuery[UserChatsTable]

}