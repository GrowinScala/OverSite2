package model.dtos

import play.api.libs.json._

case class PageLinksDTO(self: String, first: String, previous: Option[String], next: Option[String], last: String)

object PageLinksDTO {
  implicit val linksDTOFormat: OFormat[PageLinksDTO] = Json.format[PageLinksDTO]
}