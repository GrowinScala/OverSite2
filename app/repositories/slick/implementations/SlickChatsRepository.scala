
package repositories.slick.implementations

import javax.inject.Inject
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.db.NamedDatabase
import repositories.ChatsRepository

import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}


class SlickChatsRepository @Inject()
(@NamedDatabase("example") protected val dbConfigProvider: DatabaseConfigProvider)
(implicit executionContext: ExecutionContext)
	extends ChatsRepository with HasDatabaseConfigProvider[JdbcProfile] {
	
	/******* Queries here **********/
	

	
}
