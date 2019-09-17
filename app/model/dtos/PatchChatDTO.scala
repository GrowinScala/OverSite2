package model.dtos

import play.api.libs.json._

sealed abstract class PatchChatDTO(val command: String) extends Serializable

object PatchChatDTO {

  case object MoveToTrash extends PatchChatDTO("moveToTrash")

  case object Restore extends PatchChatDTO("restore")

  implicit object patchChatDTOReads extends Format[PatchChatDTO] {

    override def reads(json: JsValue): JsResult[PatchChatDTO] =
      (json \ "command").validate[String].flatMap {
        case MoveToTrash.command => JsSuccess(MoveToTrash)
        case Restore.command => JsSuccess(Restore)
        case command => JsError("The command " + command + " is not available")
      }

    override def writes(patchChatDTO: PatchChatDTO): JsValue = JsObject(Seq(
      "command" -> JsString(patchChatDTO.command)))

  }
}