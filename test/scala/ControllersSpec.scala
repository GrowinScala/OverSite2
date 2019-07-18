package scala

import controllers.ChatController

import scala.concurrent.Future
import org.scalatestplus.play._
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc._
import play.api.test._
import play.api.test.Helpers._
import repositories.ChatsRepository
import repositories.slick.implementations.SlickChatsRepository
import services.ChatService

class ControllersSpec extends PlaySpec with Results {
	
	private lazy val appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
	private lazy val injector: Injector = appBuilder.injector()
	private val cc: ControllerComponents = Helpers.stubControllerComponents()
	private val chatService = injector.instanceOf[ChatService]
	
	
	"ChatController#getChats" should {
		"be valid in" in {
			val controller = new ChatController(cc, chatService)
			val result: Future[Result] = controller.getChats("Inbox").apply(FakeRequest())
			val bodyText: String = contentAsString(result)
			bodyText mustBe "ok"
		}
	}
	

	}
