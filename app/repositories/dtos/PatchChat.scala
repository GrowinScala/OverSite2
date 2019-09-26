package repositories.dtos

abstract class PatchChat(val command: String, val patch: Option[String] = None) extends Serializable

object PatchChat {
  case object MoveToTrash extends PatchChat("moveToTrash")

  case object Restore extends PatchChat("restore")

  case class ChangeSubject(subject: String) extends PatchChat("changeSubject", Some(subject))

  object ChangeSubject {
    def unapply(str: String): Boolean = ChangeSubject("").command == str

    def unapply(patchChat: PatchChat): Option[String] = Some(patchChat.patch.getOrElse(""))
  }
}