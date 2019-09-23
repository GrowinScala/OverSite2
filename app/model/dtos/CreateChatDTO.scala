package model.dtos

import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import repositories.dtos.CreateChat

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

}

