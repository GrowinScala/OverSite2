import com.google.inject.AbstractModule
import com.google.inject.name.Names
import repositories.ChatsRepository
import repositories.slick.implementations.SlickChatsRepository
import services.{ ChatService, ChatServiceImpl }
import slick.jdbc.MySQLProfile.api._
import utils.DatabaseUtils.DEFAULT_DB

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor }

class Module extends AbstractModule {
  override def configure(): Unit = {

    implicit val ec: ExecutionContextExecutor = ExecutionContext.global
    val chatsRep = new SlickChatsRepository(DEFAULT_DB)
    val chatServ = new ChatServiceImpl(chatsRep)

    bind(classOf[Database]).toInstance(DEFAULT_DB)

    bind(classOf[ChatsRepository]).toInstance(chatsRep)

    bind(classOf[ChatService]).toInstance(chatServ)

  }
}