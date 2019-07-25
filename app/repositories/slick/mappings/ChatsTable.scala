package repositories.slick.mappings

import slick.jdbc.MySQLProfile.api._

case class ChatRow(chatId: String, subject: String)

class ChatsTable(tag: Tag) extends Table[ChatRow](tag, "chats") {
  // Columns
  def chatId = column[String]("chat_id", O.PrimaryKey)
  def subject = column[String]("subject")

  // Indexes

  // Table mapping
  override def * =
    (chatId, subject) <> (ChatRow.tupled, ChatRow.unapply)

}

object ChatsTable {
  val all = TableQuery[ChatsTable]

}