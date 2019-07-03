package controllers

import javax.inject._
import model.dtos.AddressDTO
import play.api.mvc._
import play.api.libs.json.{JsError, JsValue, Json, OFormat}
import services.AddressService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


@Singleton
class AddressController @Inject()(cc: ControllerComponents, addressService: AddressService)
	extends AbstractController(cc) {
	
	def getAddress(id: Int): Action[AnyContent] =
		Action.async { addressService.getAddress(id).map{
			case Some(addressDTO : AddressDTO) => Ok(Json.toJson(addressDTO))
			case None => NotFound
		}
		}
	
	def postAddress(): Action[JsValue] =
		Action.async(parse.json) { implicit request: Request[JsValue] =>
			val jsonValue = request.body
			jsonValue.validate[AddressDTO].fold(
				errors => Future.successful(BadRequest(JsError.toJson(errors))),
				addressDTO => addressService.postAddress(addressDTO.address).map(result =>
					Ok(Json.toJson(result))
				)
			)
		}
	
	def deleteAddress(id: Int): Action[AnyContent] =
		Action.async { addressService.deleteAddress(id).map{
			case true => NoContent
			case _ => NotFound
		}
		}
	
	
	
	
	
}
