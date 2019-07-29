package model.dtos

import play.api.libs.json.{ Json, OFormat, OWrites, Reads }

case class CreateChatDTO(chatId: Option[String], subject: String, email: CreateEmailDTO)

object CreateChatDTO {
  implicit val createChatFormat: OFormat[CreateChatDTO] = Json.format[CreateChatDTO]
  //implicit val createChatReads: Reads[CreateChatDTO] = Json.reads[CreateChatDTO]
  //implicit val createChatWrites: OWrites[CreateChatDTO] = Json.writes[CreateChatDTO]

  def tupled = (CreateChatDTO.apply _).tupled

}

