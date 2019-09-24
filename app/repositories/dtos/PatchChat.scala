package repositories.dtos

import play.api.libs.json._

abstract class PatchChat(val command: String, val patch: Option[String] = None) extends Serializable

object PatchChat {
  case object MoveToTrash extends PatchChat("moveToTrash")

  case object Restore extends PatchChat("restore")

  case class ChangeSubject(subject: String) extends PatchChat("changeSubject", Some(subject))

  object ChangeSubject {
    def unapply(str: String): Boolean = ChangeSubject("").command == str

    def unapply(patchChat: PatchChat): Option[String] = Some(patchChat.patch.getOrElse(""))
  }

  implicit object patchChatFormat extends Format[PatchChat] {

    override def reads(json: JsValue): JsResult[PatchChat] =
      (json \ "command").validate[String].flatMap {
        case MoveToTrash.command => JsSuccess(MoveToTrash)
        case Restore.command => JsSuccess(Restore)
        case ChangeSubject() => (json \ "subject").validate[String].map(subject => ChangeSubject(subject))
        case wrongCommand => JsError(s"The command $wrongCommand is not available")
      }

    override def writes(patchChat: PatchChat): JsValue =
      if (patchChat.patch.isDefined) {
        JsObject(Seq(
          "command" -> JsString(patchChat.command),
          "subject" -> JsString(patchChat.patch.getOrElse(""))))
      } else {
        JsObject(Seq(
          "command" -> JsString(patchChat.command)))
      }
  }
}