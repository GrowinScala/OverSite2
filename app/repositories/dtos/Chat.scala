package repositories.dtos

case class Chat(chatId: Int, subject: String, addresses: Set[String],
  overseers: Set[Overseers], emails: Seq[Email])