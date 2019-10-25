package repositories.RepUtils.types

sealed abstract class OrderBy(val value: String) extends Serializable

object OrderBy {
	
	case object Asc extends OrderBy("asc")
	
	case object Desc extends OrderBy("desc")
	
	case object Default extends OrderBy("default")
	
	def apply(s: String): Option[OrderBy] = s.toLowerCase match {
		case Asc.value => Some(Asc)
		case Desc.value => Some(Desc)
		case Default.value => Some(Default)
		case _ => None
		
	}
	
}
