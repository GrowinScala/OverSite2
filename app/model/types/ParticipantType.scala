package model.types

sealed abstract class ParticipantType(val value: String) extends Serializable

object ParticipantType {

  case object From extends ParticipantType("from")

  case object To extends ParticipantType("to")

  case object Cc extends ParticipantType("cc")

  case object Bcc extends ParticipantType("bcc")

  case class MissingParticipation(s: String) extends ParticipantType(s)

  val from: ParticipantType = From
  val to: ParticipantType = To
  val cc: ParticipantType = Cc
  val bcc: ParticipantType = Bcc

  def apply(s: String): ParticipantType = s.toLowerCase match {
    case From.value => From
    case To.value => To
    case Cc.value => Cc
    case Bcc.value => Bcc
    case _ => MissingParticipation(s)

  }

}

