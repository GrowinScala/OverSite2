import com.google.inject.AbstractModule
import repositories.ChatsRepository
import repositories.slick.implementations.SlickChatsRepository
import services.ChatService
import slick.jdbc.MySQLProfile.api._
import utils.DatabaseUtils.{ DEFAULT_DB, TEST_DB }

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor }

class Module extends AbstractModule {
  override def configure(): Unit = {

    implicit val ec: ExecutionContextExecutor = ExecutionContext.global
    val chatsRep = new SlickChatsRepository(TEST_DB)
    val chatServ = new ChatService(chatsRep)

    bind(classOf[Database]).toInstance(TEST_DB)

    bind(classOf[ChatsRepository]).toInstance(chatsRep)

  }
}