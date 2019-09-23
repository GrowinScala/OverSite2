package repositories.dtos

case class CreateChat(chatId: Option[String], subject: Option[String], email: UpsertEmail)
