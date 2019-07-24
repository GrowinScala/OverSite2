package repositories.dtos

import model.dtos.{ ChatDTO, EmailDTO, OverseersDTO }

/* We use a default id so that we can ommit the id on the '+=' method.
We use a negative number because we would get an error if this default value
were to be accepted*/

//case class Chat (chatId: Int = -1, subject: String)

//case class Chat (chatId: Int, subject: String)

case class Chat(chatId: Int, subject: String, addresses: Seq[String],
  overseers: Seq[Overseer], emails: Seq[Email]) {

  def toChatDTO: ChatDTO = {
    ChatDTO(
      this.chatId,
      this.subject,
      this.addresses,
      this.overseers.map(overseer =>
        OverseersDTO(
          overseer.user,
          overseer.overseers)),
      this.emails.map(email =>
        EmailDTO(
          email.emailId,
          email.from,
          email.to,
          email.bcc,
          email.cc,
          email.body,
          email.date,
          email.sent != 0,
          email.attachments)).sortBy(_.date))
  }
}
