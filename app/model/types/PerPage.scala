package model.types

import play.api.mvc.QueryStringBindable
import repositories.RepUtils.RepConstants.MAX_PER_PAGE

case class PerPage(value: Int)

object PerPage {

  val DEFAULT_PER_PAGE = PerPage(5)

  implicit def bindablePage(implicit bindableInt: QueryStringBindable[Int]): QueryStringBindable[PerPage] {
    def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, PerPage]]

    def unbind(key: String, perPage: PerPage): String
  } =
    new QueryStringBindable[PerPage] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, PerPage]] =
        bindableInt.bind(key, params) match {
          case None => Some(Right(DEFAULT_PER_PAGE))
          case Some(Right(intPerPage)) => if (intPerPage > 0) Some(Right(PerPage(intPerPage)))
          else Some(Left("The perPage value must be greater than zero"))
          case Some(Left(message)) => Some(Left(message))
        }

      override def unbind(key: String, perPage: PerPage): String =
        bindableInt.unbind(key, perPage.value)

    }

}