package model.dtos

import play.api.libs.json._

case class LinksDTO(self: String, first: String, previous: Option[String], next: Option[String], last: String)

object LinksDTO {
  implicit val linksDTOFormat: OFormat[LinksDTO] = Json.format[LinksDTO]
}