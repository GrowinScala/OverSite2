package repositories.dtos

case class Chat(chatId: String, subject: String, addresses: Set[String],
  overseers: Set[Overseers], emails: Seq[Email])