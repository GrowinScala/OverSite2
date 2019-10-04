package model.types

import play.api.mvc.QueryStringBindable

case class PerPage(value: Int)

object PerPage {

  implicit def bindablePage(implicit bindableInt: QueryStringBindable[Int]): QueryStringBindable[PerPage] {
    def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, PerPage]]

    def unbind(key: String, perPage: PerPage): String
  } =
    new QueryStringBindable[PerPage] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, PerPage]] =
        bindableInt.bind(key, params) match {
          case None => Some(Right(PerPage(5)))
          case Some(Right(intPerPage)) => if (intPerPage > 0) Some(Right(PerPage(intPerPage)))
          else Some(Left("The perPage value must be greater than zero"))
          case Some(Left(message)) => Some(Left(message))
        }

      override def unbind(key: String, perPage: PerPage): String =
        bindableInt.unbind(key, perPage.value)

    }

}