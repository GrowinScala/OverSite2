package model.dtos

import play.api.libs.json._

case class PaginationDTO(totalCount: Int, links: PageLinksDTO)

object PaginationDTO {
  implicit val PaginationDTOFormat: OFormat[PaginationDTO] = Json.format[PaginationDTO]
}