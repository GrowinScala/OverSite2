package model.types

import play.api.mvc.QueryStringBindable

case class Page(value: Int) {
  self =>

  def >(that: Page): Boolean =
    self.value > that.value

  def >(that: Int): Boolean =
    self.value > that

  def ==(that: Page): Boolean =
    self.value == that.value

  def ==(that: Int): Boolean =
    self.value == that

  def >=(that: Page): Boolean =
    self.value >= that.value

  def >=(that: Int): Boolean =
    self.value >= that

  def +(that: Int): Page =
    Page(self.value + that)

  def -(that: Int): Page =
    Page(self.value - that)
}

object Page {

  val DEFAULT_PAGE = Page(0)

  implicit def bindablePage(implicit bindableInt: QueryStringBindable[Int]): QueryStringBindable[Page] {
    def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Page]]

    def unbind(key: String, page: Page): String
  } =
    new QueryStringBindable[Page] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Page]] =
        bindableInt.bind(key, params) match {
          case None => Some(Right(DEFAULT_PAGE))
          case Some(Right(intPage)) => if (intPage >= 0) Some(Right(Page(intPage)))
          else Some(Left("The page number must not be negative"))
          case Some(Left(message)) => Some(Left(message))
        }

      override def unbind(key: String, page: Page): String =
        bindableInt.unbind(key, page.value)

    }

}