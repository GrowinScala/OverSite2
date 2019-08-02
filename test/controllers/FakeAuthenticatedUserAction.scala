package controllers

import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }
import play.api.mvc
import play.api.mvc.{ AnyContent, BodyParser, Request, Result }

import scala.concurrent.{ ExecutionContext, Future }

class FakeAuthenticatedUserAction extends AuthenticatedUserAction {

  val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: Materializer = ActorMaterializer()
  override def parser: BodyParser[AnyContent] = new mvc.BodyParsers.Default()

  override def invokeBlock[A](
    request: Request[A],
    block: AuthenticatedUser[A] => Future[Result]): Future[Result] =
    block(AuthenticatedUser("00000000-0000-0000-0000-000000000000", request))

}