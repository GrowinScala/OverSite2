package model.dtos

import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import repositories.dtos.{ Chat, CreateChat, Email }

case class CreateChatDTO(chatId: Option[String], subject: Option[String], email: UpsertEmailDTO)

object CreateChatDTO {
  implicit val createChatWrites: OWrites[CreateChatDTO] = Json.writes[CreateChatDTO]

  implicit val createChatDTOReads: Reads[CreateChatDTO] = (
    (JsPath \ "chatId").readNullable[String] and
    (JsPath \ "subject").readNullable[String] and
    (JsPath \ "email").read[UpsertEmailDTO])(CreateChatDTO.apply _)

  def tupled = (CreateChatDTO.apply _).tupled

  def toCreateChat(createChatDTO: CreateChatDTO): CreateChat = {
    CreateChat(
      chatId = createChatDTO.chatId,
      subject = createChatDTO.subject,
      email = UpsertEmailDTO.toUpsertEmail(createChatDTO.email))
  }

  def toCreateChatDTO(createChat: CreateChat): CreateChatDTO = {
    CreateChatDTO(
      chatId = createChat.chatId,
      subject = createChat.subject,
      email = UpsertEmailDTO.toUpsertEmailDTO(createChat.email))
  }

  /**
   * Method that transforms an instance of the CreateChat class into an instance of Chat
   * @param chat instance of the class CreateChat
   * @return an instance of class Chat
   */
  def fromCreateChatDTOtoChat(chat: CreateChatDTO): Chat = {
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

