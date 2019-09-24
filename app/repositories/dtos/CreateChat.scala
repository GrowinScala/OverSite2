package repositories.dtos

case class CreateChat(chatId: Option[String], subject: Option[String], email: UpsertEmail)

object CreateChat {
  def fromCreateChatToChat(chat: CreateChat): Chat = {
    val email = chat.email

    Chat(
      chatId = chat.chatId.getOrElse(""),
      subject = chat.subject.getOrElse(""),
      addresses = email.from.toSet ++ email.to.getOrElse(Set()) ++ email.bcc.getOrElse(Set()) ++ email.cc.getOrElse(Set()),
      overseers = Set(),
      emails = Seq(
        Email(
          emailId = email.emailId.getOrElse(""),
          from = email.from.getOrElse(""),
          to = email.to.getOrElse(Set()),
          bcc = email.bcc.getOrElse(Set()),
          cc = email.cc.getOrElse(Set()),
          body = email.body.getOrElse(""),
          date = email.date.getOrElse(""),
          sent = 0,
          attachments = Set())))
  }
}
