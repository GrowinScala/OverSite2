package repositories.dtos

case class Chat(chatId: String, subject: String, addresses: Seq[String],
  overseers: Seq[Overseer], emails: Seq[Email])
