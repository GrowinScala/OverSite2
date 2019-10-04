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

  implicit def bindableMailbox(implicit bindableString: QueryStringBindable[String]): QueryStringBindable[Mailbox] {
    def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Mailbox]]

    def unbind(key: String, mailbox: Mailbox): String
  } =
    new QueryStringBindable[Mailbox] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Mailbox]] =
        params.get(key) match {
          case None => Some(Right(Inbox))
          case Some(seq) => seq.headOption.flatMap(Mailbox(_)) match {
            case Some(mailbox) => Some(Right(mailbox))
            case None => Some(Left("Wrong mailbox parameter"))
          }
        }

      override def unbind(key: String, mailbox: Mailbox): String = {
        bindableString.unbind(key, mailbox.value)

      }
    }
}

