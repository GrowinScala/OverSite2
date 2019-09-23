package model.dtos

import play.api.libs.json._

abstract class PatchChatDTO(val command: String, val patch: Option[String] = None) extends Serializable

object PatchChatDTO {
  case object MoveToTrash extends PatchChatDTO("moveToTrash")

  case object Restore extends PatchChatDTO("restore")

  case class ChangeSubject(subject: String) extends PatchChatDTO("changeSubject", Some(subject))

  object ChangeSubject {
    def unapply(str: String): Boolean = ChangeSubject("").command == str

    def unapply(patchChatDTO: PatchChatDTO): Option[String] = Some(patchChatDTO.patch.getOrElse(""))
  }

  implicit object patchChatDTOReads extends Format[PatchChatDTO] {

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
}