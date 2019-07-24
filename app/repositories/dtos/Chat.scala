package repositories.dtos

case class Chat(chatId: Int, subject: String, addresses: Seq[String],
  overseers: Seq[Overseers], emails: Seq[Email])