package model.types

import play.api.mvc.QueryStringBindable
import repositories.RepUtils.types.OrderBy
import repositories.RepUtils.types.OrderBy._

case class Sort(sortBy: String, orderBy: OrderBy)

object Sort {

  val DEFAULT_SORT = "default"
  val SORT_BY_DATE = "date"

  implicit def bindableSort(implicit bindableString: QueryStringBindable[String]): QueryStringBindable[Sort] {
    def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Sort]]

    def unbind(key: String, sort: Sort): String
  } =
    new QueryStringBindable[Sort] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Sort]] =
        bindableString.bind(key, params) match {
          case None => Some(Right(Sort(DEFAULT_SORT, DefaultOrder)))
          case Some(Right(sort)) if sort.isEmpty => Some(Left("Invalid sorting value"))
          case Some(Right(sort)) if sort.head == '-' => Some(Right(Sort(sort.tail, Desc)))
          case Some(Right(sort)) if sort.head == '+' => Some(Right(Sort(sort.tail, Asc)))
          case Some(Right(sort)) => Some(Right(Sort(sort, DefaultOrder)))
          case Some(Left(message)) => Some(Left(message))
        }

      override def unbind(key: String, sort: Sort): String = {
        val prefix = sort.orderBy match {
          case Asc => "+"
          case Desc => "-"
          case DefaultOrder => ""
        }

        bindableString.unbind(key, prefix ++ sort.sortBy)

      }

    }
}