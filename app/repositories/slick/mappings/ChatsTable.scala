package repositories.slick.mappings

import slick.jdbc.MySQLProfile.api._

case class ChatRow(chatId: Int, subject: String)

class ChatsTable(tag: Tag) extends Table[ChatRow](tag, "chats") {
  // Columns
  def chatId = column[Int]("chat_id", O.PrimaryKey, O.AutoInc)
  def subject = column[String]("subject")

  // Indexes

  // Table mapping
  override def * =
    (chatId, subject) <> (ChatRow.tupled, ChatRow.unapply)

}

object ChatsTable {
  val all = TableQuery[ChatsTable]

}