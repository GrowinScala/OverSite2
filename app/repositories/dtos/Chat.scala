package repositories.dtos

/* We use a default id so that we can ommit the id on the '+=' method.
We use a negative number because we would get an error if this default value
were to be accepted*/

//case class Chat (chatId: Int = -1, subject: String)

//case class Chat (chatId: Int, subject: String)

case class Chat(chatId: Int, subject: String, addresses: Seq[String],
  overseers: Seq[Overseer], emails: Seq[Email])
