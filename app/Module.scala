import com.google.inject.AbstractModule
import com.google.inject.name.Names
import repositories.ChatsRepository
import repositories.slick.implementations.SlickChatsRepository
import services.ChatService
import slick.jdbc.MySQLProfile.api._
import utils.DatabaseUtils.DEFAULT_DB

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor }

class Module extends AbstractModule {
  def configure(): Unit = {

    implicit val ec = ExecutionContext.global

    bind(classOf[Database]).toInstance(DEFAULT_DB)

    bind(classOf[ChatsRepository]).toInstance(new SlickChatsRepository(DEFAULT_DB))

  }
}