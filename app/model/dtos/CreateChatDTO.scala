package model.dtos

import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

case class CreateChatDTO(chatId: Option[String], subject: Option[String], email: CreateEmailDTO)

object CreateChatDTO {
  implicit val createChatWrites: OWrites[CreateChatDTO] = Json.writes[CreateChatDTO]

  implicit val createChatDTOReads: Reads[CreateChatDTO] = (
    (JsPath \ "chatId").readNullable[String] and
    (JsPath \ "subject").readNullable[String] and
    (JsPath \ "email").read[CreateEmailDTO])(CreateChatDTO.apply _)

  def tupled = (CreateChatDTO.apply _).tupled

}

