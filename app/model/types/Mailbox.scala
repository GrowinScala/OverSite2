package model.types

sealed abstract class Mailbox(val value: String) extends Serializable

object Mailbox {
	
	case object Inbox extends Mailbox("inbox")
	
	case object Sent extends Mailbox("sent")
	
	case object Trash extends Mailbox("trash")
	
	case object Drafts extends Mailbox("drafts")
	
	case object Overseeing extends Mailbox("overseeing")
	
	
	def apply(s : String) : Mailbox = s.toLowerCase match {
		case Inbox.value => Inbox
		case Sent.value => Sent
		case Trash.value => Trash
		case Drafts.value => Drafts
		case Overseeing.value => Overseeing
		
	}
}
