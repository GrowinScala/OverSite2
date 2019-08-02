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
  //val insert = (all.returning(all.map(_.chatId)))

  /*
  val userId =
  (users returning users.map(_.id)) += User(None, "Stefan", "Zeiger")

  val userWithId =
  (users returning users.map(_.id)
         into ((user,id) => user.copy(id=Some(id)))
  ) += User(None, "Stefan", "Zeiger")
   */

}