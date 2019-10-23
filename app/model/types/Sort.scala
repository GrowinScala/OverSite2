package model.types

import play.api.mvc.QueryStringBindable
import repositories.RepUtils.RepConstants.DEFAULT_SORT

case class Sort(value: String)

object Sort {
	
	implicit def bindableSort(implicit bindableString: QueryStringBindable[String]): QueryStringBindable[Sort] {
		def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Sort]]
		
		def unbind(key: String, sort: Sort): String
	} =
		new QueryStringBindable[Sort] {
			override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Sort]] =
				bindableString.bind(key, params) match {
					case None => Some(Right(Sort(DEFAULT_SORT)))
					case Some(Right(stringSort))  => Some(Right(Sort(0)))
					case Some(Right(intSort)) => if (intSort >= 0) Some(Right(Sort(intSort)))
					                             else Some(Left("The sort number must not be negative"))
					case Some(Left(message)) => Some(Left(message))
				}
			
			override def unbind(key: String, sort: Sort): String =
				bindableString.unbind(key, sort.value)
			
		}
}