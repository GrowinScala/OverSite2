import com.google.inject.AbstractModule
import controllers.{ AuthenticatedUserAction, FakeAuthenticatedUserAction }
import repositories.{ AuthenticationRepository, ChatsRepository }
import repositories.slick.implementations.{ SlickAuthenticationRepository, SlickChatsRepository }
import services.ChatService
import slick.jdbc.MySQLProfile.api._
import utils.DatabaseUtils.{ DEFAULT_DB, TEST_DB }

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor }

class Module extends AbstractModule {
  override def configure(): Unit = {

    implicit val ec: ExecutionContextExecutor = ExecutionContext.global
    val chatsRep = new SlickChatsRepository(TEST_DB)
    val authenticationRep = new SlickAuthenticationRepository(TEST_DB)

    bind(classOf[Database]).toInstance(TEST_DB)

    bind(classOf[ChatsRepository]).toInstance(chatsRep)
    bind(classOf[AuthenticationRepository]).toInstance(authenticationRep)
    bind(classOf[AuthenticatedUserAction]).to(classOf[FakeAuthenticatedUserAction])

  }
}