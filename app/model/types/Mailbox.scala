package model.types

import play.api.mvc.QueryStringBindable

sealed abstract class Mailbox(val value: String) extends Serializable

object Mailbox {

  case object Inbox extends Mailbox("inbox")

  case object Sent extends Mailbox("sent")

  case object Drafts extends Mailbox("drafts")

  case object Trash extends Mailbox("trash")

  def apply(s: String): Option[Mailbox] = s.toLowerCase match {
    case Inbox.value => Some(Inbox)
    case Sent.value => Some(Sent)
    case Drafts.value => Some(Drafts)
    case Trash.value => Some(Trash)
    case _ => None

  }

  implicit def bindableMailbox(implicit bindableString: QueryStringBindable[String]) =
    new QueryStringBindable[Mailbox] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Mailbox]] = {
        params.get(key).flatMap(_.headOption).flatMap(Mailbox(_)) match {
          case Some(mailbox) => Some(Right(mailbox))
          case None => Some(Left("Wrong mailbox parameter"))
        }
      }

      override def unbind(key: String, mailbox: Mailbox): String = {
        bindableString.unbind(key, mailbox.value)

      }
    }
}

