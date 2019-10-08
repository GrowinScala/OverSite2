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

  def toSeqPostOverseer(postOverseersDTO: Seq[PostOverseerDTO]): Seq[PostOverseer] =
    postOverseersDTO.map(postOverseerDTO => PostOverseer(postOverseerDTO.address, postOverseerDTO.oversightId))

  def toSeqPostOverseerDTO(postOverseers: Seq[PostOverseer]): Seq[PostOverseerDTO] =
    postOverseers.map(postOverseer => PostOverseerDTO(postOverseer.address, postOverseer.oversightId))

  def toSetPostOverseer(postOverseersDTO: Set[PostOverseerDTO]): Set[PostOverseer] =
    postOverseersDTO.map(postOverseerDTO => PostOverseer(postOverseerDTO.address, postOverseerDTO.oversightId))

  def toSetPostOverseerDTO(postOverseers: Set[PostOverseer]): Set[PostOverseerDTO] =
    postOverseers.map(postOverseer => PostOverseerDTO(postOverseer.address, postOverseer.oversightId))
}

