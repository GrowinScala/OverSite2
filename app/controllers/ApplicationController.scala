package controllers

import javax.inject._
import play.api.mvc._
import services.AddressService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class ApplicationController @Inject() (cc: ControllerComponents, addressService: AddressService) extends AbstractController(cc) {

}
