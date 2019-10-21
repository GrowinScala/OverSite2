package controllers

import javax.inject.Inject
import play.api.mvc._

import scala.concurrent.{ ExecutionContext, Future }

class FakeAuthenticatedUserAction @Inject() (defaultParser: BodyParsers.Default, implicit val executionContext: ExecutionContext) extends AuthenticatedUserAction {

  override def parser: BodyParser[AnyContent] = defaultParser

  override def invokeBlock[A](
    request: Request[A],
    block: AuthenticatedUser[A] => Future[Result]): Future[Result] =
    block(AuthenticatedUser("00000000-0000-0000-0000-000000000000", request))

}