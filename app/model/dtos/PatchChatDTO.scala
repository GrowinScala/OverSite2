package model.dtos

import play.api.libs.json._
import repositories.dtos.PatchChat

abstract class PatchChatDTO(val command: String, val patch: Option[String] = None) extends Serializable

object PatchChatDTO {
  case object MoveToTrash extends PatchChatDTO("moveToTrash")

  case object Restore extends PatchChatDTO("restore")

  case class ChangeSubject(subject: String) extends PatchChatDTO("changeSubject", Some(subject))

  object ChangeSubject {
    def unapply(str: String): Boolean = ChangeSubject("").command == str

    def unapply(patchChatDTO: PatchChatDTO): Option[String] = Some(patchChatDTO.patch.getOrElse(""))
  }

  implicit object patchChatDTOFormat extends Format[PatchChatDTO] {

    override def reads(json: JsValue): JsResult[PatchChatDTO] =
      (json \ "command").validate[String].flatMap {
        case MoveToTrash.command => JsSuccess(MoveToTrash)
        case Restore.command => JsSuccess(Restore)
        case ChangeSubject() => (json \ "subject").validate[String].map(subject => ChangeSubject(subject))
        case wrongCommand => JsError(s"The command $wrongCommand is not available")
      }

    override def writes(patchChatDTO: PatchChatDTO): JsValue =
      if (patchChatDTO.patch.isDefined) {
        JsObject(Seq(
          "command" -> JsString(patchChatDTO.command),
          "subject" -> JsString(patchChatDTO.patch.getOrElse(""))))
      } else {
        JsObject(Seq(
          "command" -> JsString(patchChatDTO.command)))
      }
  }

  def toPatchChat(patchChatDTO: PatchChatDTO): PatchChat = {
    patchChatDTO match {
      case PatchChatDTO.MoveToTrash => PatchChat.MoveToTrash
      case PatchChatDTO.Restore => PatchChat.Restore
      case PatchChatDTO.ChangeSubject(subject) => PatchChat.ChangeSubject(subject)
    }
  }

  def toPatchChatDTO(patchChat: PatchChat): PatchChatDTO = {
    patchChat match {
      case PatchChat.MoveToTrash => PatchChatDTO.MoveToTrash
      case PatchChat.Restore => PatchChatDTO.Restore
      case PatchChat.ChangeSubject(subject) => PatchChatDTO.ChangeSubject(subject)
    }
  }
}