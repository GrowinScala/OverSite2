package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json.{JsValue, Json, OFormat}
import repositories.dtos.{AddressDTO, CreateAddressDTO}
import services.AddressService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class ApplicationController @Inject()(cc: ControllerComponents, addressService: AddressService) extends AbstractController(cc) {

  implicit val addressFormat : OFormat[AddressDTO] = Json.format[AddressDTO]

  def getAddress(id: Int): Action[AnyContent] =
    Action.async { addressService.getAddress(id).map{
        case Some(addressDTO : AddressDTO) => Ok(Json.toJson(addressDTO))
        case None => NotFound
      }
    }

  def postAddress(): Action[JsValue] =
    Action.async(parse.json) { implicit request: Request[JsValue] =>
      val jsonValue = request.body
      val createAddressDTO = jsonValue.as[CreateAddressDTO]
      addressService.postAddress(createAddressDTO).map{ result =>
        Ok(Json.toJson(result))
      }
    }
	
	def deleteAddress(id: Int): Action[AnyContent] =
		Action.async { addressService.deleteAddress(id).map{
				case true => NoContent
				case _ => NotFound
			}
		}
	
	
	
	

}
