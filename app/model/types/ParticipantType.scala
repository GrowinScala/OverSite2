package model.types

sealed abstract class ParticipantType(val value: String) extends Serializable

object ParticipantType {

  case object From extends ParticipantType("from")

  case object To extends ParticipantType("to")

  case object Cc extends ParticipantType("cc")

  case object Bcc extends ParticipantType("bcc")

  def apply(s: String): Option[ParticipantType] = s.toLowerCase match {
    case From.value => Some(From)
    case To.value => Some(To)
    case Cc.value => Some(Cc)
    case Bcc.value => Some(Bcc)
    case _ => None

  }

}

