package controllers


import javax.inject._
import model.dtos.{AddressDTO, ChatDTO, EmailDTO, OverseersDTO}
import play.api.mvc._
import play.api.libs.json.{JsError, JsValue, Json, OFormat}
import services.{AddressService, ChatService}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


@Singleton
class ChatController @Inject()(cc: ControllerComponents, chatService: ChatService)
	extends AbstractController(cc) {
	
	def getChat(id : Int) : Action[AnyContent] =
		Action.async {
			// TODO Hard-coded userId = 1
			val userId = 1

				chatService.getChat(id, userId).map{emaildto =>
				Ok(Json.toJson(emaildto))
			}
		}

		/*
		// Hard-coded
		Action.async {

			val overseersDTO = OverseersDTO("valter@mail.com", Array("pedrol@mail.com", "pedroc@mail.com", "rui@mail.com"))
			val emailDTO = EmailDTO(1, "valter@mail.com", Array("beatriz@mail.com", "joao@mail.com"), Array(), Array(),
				"2019-06-17 10:00:00", "Olá Beatriz e João! Vamos começar o projeto.", Array(), true)

			val chatDTO = ChatDTO(1, "Projeto Oversite2", Array("valter@mail.com", "joao@mail.com", "beatriz@mail.com"),
				Array(overseersDTO), Array(emailDTO))

			Future(Ok(Json.toJson(chatDTO)))
		}*/


	

}
