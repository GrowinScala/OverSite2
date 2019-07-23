import com.google.inject.AbstractModule
import services.ChatService
import slick.jdbc.MySQLProfile.api._
import utils.DatabaseUtils.TEST_DB

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor }

class Module extends AbstractModule {
  override def configure(): Unit = {

    implicit val ec: ExecutionContextExecutor = ExecutionContext.global

    bind(classOf[Database]).toInstance(TEST_DB)

  }
}