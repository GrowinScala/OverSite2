package controllers

import javax.inject._
import play.api.libs.json.{ Json, OFormat }
import play.api.mvc._
import repositories.slick.implementations.SlickChatsRepository
import repositories.slick.mappings.UserChatRow
import services.AddressService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class ApplicationController @Inject() (cc: ControllerComponents, chatRep: SlickChatsRepository) extends AbstractController(cc) {

  def test(mailbox: String) =
    ???
  /*	Action.async {
			chatRep.test.map(array => Ok(Json.toJson(array)))
		}
	*/

}
