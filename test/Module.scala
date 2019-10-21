import com.google.inject.AbstractModule
import controllers.{ AuthenticatedUserAction, FakeAuthenticatedUserAction }
import repositories.{ AuthenticationRepository, ChatsRepository }
import repositories.slick.implementations.{ SlickAuthenticationRepository, SlickChatsRepository }

import slick.jdbc.MySQLProfile.api._
import utils.DatabaseUtils.TEST_DB

class Module extends AbstractModule {
  override def configure(): Unit = {

    bind(classOf[Database]).toInstance(TEST_DB)

    bind(classOf[ChatsRepository]).to(classOf[SlickChatsRepository])
    bind(classOf[AuthenticationRepository]).to(classOf[SlickAuthenticationRepository])
    bind(classOf[AuthenticatedUserAction]).to(classOf[FakeAuthenticatedUserAction])

  }
}