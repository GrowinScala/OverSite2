package model.dtos

import play.api.libs.json._

case class PaginationDTO(totalCount: Int, links: LinksDTO)

object PaginationDTO {
  implicit val PaginationDTOFormat: OFormat[PaginationDTO] = Json.format[PaginationDTO]
}