package repositories.dtos

/* We use a default id so that we can ommit the id on the '+=' method.
We use a negative number because we would get an error if this default value
were to be accepted*/

case class Oversight(oversightId: Int = -1, userChatId: Int, userId: Int)
