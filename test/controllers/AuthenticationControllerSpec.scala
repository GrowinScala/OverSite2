package controllers

import org.scalatestplus.play._
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import services.AuthenticationService
import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.OptionValues
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import utils.Jsons._
import utils.TestGenerators._

import scala.concurrent.{ ExecutionContext, Future }

class AuthenticationControllerSpec extends PlaySpec with OptionValues with Results
  with IdiomaticMockito with GuiceOneAppPerSuite with Injecting {

  private lazy val appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
  private lazy val injector: Injector = appBuilder.injector()
  implicit val ec: ExecutionContext = injector.instanceOf[ExecutionContext]
  implicit val cc: ControllerComponents = injector.instanceOf[ControllerComponents]
  implicit val defaultParser: BodyParsers.Default = injector.instanceOf[BodyParsers.Default]

  def getControllerServiceMockAndAuthAction: (AuthenticationController, AuthenticationService, AuthenticatedUserAction) = {

    implicit val mockAuthenticationService: AuthenticationService = mock[AuthenticationService]
    val authenticationController = new AuthenticationController()
    val authenticatedUserAction = new ImplAuthenticatedUserAction()
    (authenticationController, mockAuthenticationService, authenticatedUserAction)
  }

  //region AuthenticationController#signUpUser
  "AuthenticationController#signUpUser" should {

    "point out bad Json request" in {
      val (authenticationController, _, _) = getControllerServiceMockAndAuthAction
      val request = FakeRequest().withHeaders(CONTENT_TYPE -> "application/json").withBody(
        genBadSignJSON.sample.value)
      val result = authenticationController.signUpUser.apply(request)
      status(result) mustBe BAD_REQUEST
    }

    "transmit the service error message" in {
      val jsonMessage = genSimpleJsObj.sample.value
      val (authenticationController, mockAuthenticationService, _) = getControllerServiceMockAndAuthAction
      mockAuthenticationService.signUpUser(*)
        .returns(Future.successful(Left(jsonMessage)))

      val request = FakeRequest().withHeaders(CONTENT_TYPE -> "application/json").withBody(Json.toJson(
        genUserAccessDTO.sample.value))
      val result = authenticationController.signUpUser.apply(request)
      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe jsonMessage
    }

    "return token" in {
      val jsonToken = jsToken(genUUID.sample.value)

      val (authenticationController, mockAuthenticationService, _) = getControllerServiceMockAndAuthAction
      mockAuthenticationService.signUpUser(*)
        .returns(Future.successful(Right(jsonToken)))

      val request = FakeRequest().withHeaders(CONTENT_TYPE -> "application/json").withBody(Json.toJson(
        Json.toJson(genUserAccessDTO.sample.value)))
      val result = authenticationController.signUpUser.apply(request)
      status(result) mustBe OK
      contentAsJson(result) mustBe jsonToken
    }
  }
  //endregion

  //region AuthenticationController#signInUser
  "AuthenticationController#signInUser" should {

    "point out bad Json request" in {
      val (authenticationController, _, _) = getControllerServiceMockAndAuthAction
      val request = FakeRequest().withHeaders(CONTENT_TYPE -> "application/json")
        .withBody(genBadSignJSON.sample.value)

      val result = authenticationController.signInUser.apply(request)
      status(result) mustBe BAD_REQUEST
    }

    "transmit the service error message" in {
      val jsonMessage = genSimpleJsObj.sample.value

      val (authenticationController, mockAuthenticationService, _) = getControllerServiceMockAndAuthAction
      mockAuthenticationService.signInUser(*)
        .returns(Future.successful(Left(jsonMessage)))

      val request = FakeRequest().withHeaders(CONTENT_TYPE -> "application/json")
        .withBody(Json.toJson(genUserAccessDTO.sample.value))
      val result = authenticationController.signInUser.apply(request)
      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe jsonMessage
    }

    "transmit internal error" in {
      val (authenticationController, mockAuthenticationService, _) = getControllerServiceMockAndAuthAction
      mockAuthenticationService.signInUser(*)
        .returns(Future.successful(Left(internalError)))

      val request = FakeRequest().withHeaders(CONTENT_TYPE -> "application/json")
        .withBody(Json.toJson(genUserAccessDTO.sample.value))
      val result = authenticationController.signInUser.apply(request)
      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "return token" in {
      val jsonToken = jsToken(genUUID.sample.value)

      val (authenticationController, mockAuthenticationService, _) = getControllerServiceMockAndAuthAction
      mockAuthenticationService.signInUser(*)
        .returns(Future.successful(Right(jsonToken)))

      val request = FakeRequest().withHeaders(CONTENT_TYPE -> "application/json").withBody(Json.toJson(
        Json.toJson(genUserAccessDTO.sample.value)))
      val result = authenticationController.signInUser.apply(request)
      status(result) mustBe OK
      contentAsJson(result) mustBe jsonToken
    }

  }
  //endregion

  //region ImplAuthenticatedUserAction#invokeBlock
  "ImplAuthenticatedUserAction#invokeBlock" should {

    "point out missing Token" in {
      val (_, _, authenticatedUserAction) =
        getControllerServiceMockAndAuthAction

      val result = authenticatedUserAction.async(
        authenticatedUser => Future.successful(Ok)).apply(FakeRequest())
      status(result) mustBe UNAUTHORIZED
      contentAsJson(result) mustBe tokenNotFound
    }

    "transmit the service error message" in {
      val jsonMessage = genSimpleJsObj.sample.value

      val (_, mockAuthenticationService, authenticatedUserAction) =
        getControllerServiceMockAndAuthAction
      mockAuthenticationService.validateToken(*)
        .returns(Future.successful(Left(jsonMessage)))

      val result = authenticatedUserAction.async(
        authenticatedUser => Future.successful(Ok)).apply(FakeRequest().withHeaders("Authorization" ->
          genUUID.sample.value))
      status(result) mustBe UNAUTHORIZED
      contentAsJson(result) mustBe jsonMessage
    }

    "forward to block" in {
      val userId = genUUID.sample.value
      val (_, mockAuthenticationService, authenticatedUserAction) =
        getControllerServiceMockAndAuthAction
      mockAuthenticationService.validateToken(*)
        .returns(Future.successful(Right(userId)))

      val result = authenticatedUserAction.invokeBlock[AnyContent](
        FakeRequest().withHeaders("Authorization" -> genUUID.sample.value),
        authenticatedUser => Future.successful(Ok(authenticatedUser.userId)))
      status(result) mustBe OK
      contentAsString(result) mustBe userId
    }

  }
  //endregion

}
