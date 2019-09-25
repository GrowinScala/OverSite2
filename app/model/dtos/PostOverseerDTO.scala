package model.dtos
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import repositories.dtos.PostOverseer

case class PostOverseerDTO(address: String, oversightId: Option[String])

object PostOverseerDTO {
  implicit val OverseerDataOFormat: OFormat[PostOverseerDTO] = (
    (JsPath \ "address").format[String](email) and
    (JsPath \ "oversightId").formatNullable[String])(PostOverseerDTO.apply, unlift(PostOverseerDTO.unapply))

  def toPostOverseer(postOverseerDTO: PostOverseerDTO): PostOverseer =
    PostOverseer(postOverseerDTO.address, postOverseerDTO.oversightId)

  def toPostOverseerDTO(postOverseer: PostOverseer): PostOverseerDTO =
    PostOverseerDTO(postOverseer.address, postOverseer.oversightId)
}

