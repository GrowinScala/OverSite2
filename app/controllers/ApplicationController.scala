package controllers

import javax.inject._
import repositories.Address
import play.api.Logging
import play.api.mvc._
import services.AddressService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class ApplicationController @Inject()(cc: ControllerComponents, addressService: AddressService) extends AbstractController(cc) with Logging {

  def getAddress(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    addressService.getAddress(id) map { address =>
      Ok("Address with id " + id + " is " + address)
    }
  }

}
