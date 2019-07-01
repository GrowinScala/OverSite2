package repositories.dtos

/* We use a default id so that we can ommit the id on the '+=' method.
We use a negative number because we would get an error if this default value
were to be accepted*/

case class UserChat (userChatId: Int = -1, userId: Int, chatId: Int, mailBox: String)
