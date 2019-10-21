package repositories.dtos

import java.time.LocalDateTime

import play.api.libs.json._

case class Jwt(userId: String, expirationDate: LocalDateTime)

object Jwt {

  implicit val jwtFormat: OFormat[Jwt] = Json.format[Jwt]
}
