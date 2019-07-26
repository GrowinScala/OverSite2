package repositories.slick.mappings

import slick.jdbc.MySQLProfile.api._

case class UserChatRow(userChatId: String, userId: String, chatId: String, inbox: Int, sent: Int, draft: Int, trash: Int)

class UserChatsTable(tag: Tag) extends Table[UserChatRow](tag, "user_chats") {
  // Columns
  def userChatId = column[String]("user_chat_id", O.PrimaryKey)
  def userId = column[String]("user_id")
  def chatId = column[String]("chat_id")
  def inbox = column[Int]("inbox")
  def sent = column[Int]("sent")
  def draft = column[Int]("draft")
  def trash = column[Int]("trash")

  // Indexes

  // Table mapping
  override def * =
    (userChatId, userId, chatId, inbox, sent, draft, trash) <> (UserChatRow.tupled, UserChatRow.unapply)

}

object UserChatsTable {
  val all = TableQuery[UserChatsTable]

}