import com.google.inject.AbstractModule
import controllers.{ AuthenticatedUserAction, ImplAuthenticatedUserAction }
import repositories.{ AuthenticationRepository, ChatsRepository }
import repositories.slick.implementations.{ SlickAuthenticationRepository, SlickChatsRepository }
import slick.jdbc.MySQLProfile.api._
import utils.DatabaseUtils.DEFAULT_DB

import scala.concurrent.ExecutionContext.Implicits.global

class Module extends AbstractModule {
  override def configure(): Unit = {

    val chatsRep = new SlickChatsRepository(DEFAULT_DB)
    val authenticationRep = new SlickAuthenticationRepository(DEFAULT_DB)

    bind(classOf[Database]).toInstance(DEFAULT_DB)

    bind(classOf[ChatsRepository]).toInstance(chatsRep)
    bind(classOf[AuthenticationRepository]).toInstance(authenticationRep)
    bind(classOf[AuthenticatedUserAction]).to(classOf[ImplAuthenticatedUserAction])

  }
}