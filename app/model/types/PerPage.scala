package model.types

import play.api.mvc.QueryStringBindable
import repositories.RepUtils.RepConstants._

case class PerPage(value: Int)

object PerPage {

  implicit def bindablePage(implicit bindableInt: QueryStringBindable[Int]): QueryStringBindable[PerPage] {
    def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, PerPage]]

    def unbind(key: String, perPage: PerPage): String
  } =
    new QueryStringBindable[PerPage] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, PerPage]] =
        bindableInt.bind(key, params) match {
          case None => Some(Right(PerPage(DEFAULT_PER_PAGE)))
          case Some(Right(intPerPage)) if intPerPage < 0 =>
            Some(Left("The perPage value must be greater than zero"))
          case Some(Right(intPerPage)) if intPerPage > MAX_PER_PAGE =>
            Some(Left(s"The perPage value must not be greater than $MAX_PER_PAGE"))

          case Some(Right(intPerPage)) => Some(Right(PerPage(intPerPage)))
          case Some(Left(message)) => Some(Left(message))
        }

      override def unbind(key: String, perPage: PerPage): String =
        bindableInt.unbind(key, perPage.value)

    }

}