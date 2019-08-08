package controllers

import model.dtos.UserAccessDTO
import org.scalatestplus.play._
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import services.AuthenticationService
import org.mockito.scalatest.IdiomaticMockito
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import utils.Jsons._

import scala.concurrent.Future

class AuthenticationControllerSpec extends PlaySpec with Results with IdiomaticMockito with GuiceOneAppPerSuite
  with Injecting {

  private val cc: ControllerComponents = inject[ControllerComponents]

  //region AuthenticationController#signUpUser
  "AuthenticationController#signUpUser" should {

    "point out bad Json request" in {
      val mockAuthenticationService = mock[AuthenticationService]
      val authenticationController = new AuthenticationController(cc, mockAuthenticationService)
      val request = FakeRequest().withHeaders(CONTENT_TYPE -> "application/json").withBody(Json.parse(
        """{"address" : "inmproper address", "password": "test"} """))
      val result = authenticationController.signUpUser.apply(request)
      status(result) mustBe BAD_REQUEST
    }

    "transmit the service error message" in {
      val mockAuthenticationService = mock[AuthenticationService]
      mockAuthenticationService.signUpUser(*)
        .returns(Future.successful((UserAccessDTO("", "", None, None, None), Some(testMessage))))

      val authenticationController = new AuthenticationController(cc, mockAuthenticationService)
      val request = FakeRequest().withHeaders(CONTENT_TYPE -> "application/json").withBody(Json.parse(
        """{"address" : "test@mail.com", "password": "test"} """))
      val result = authenticationController.signUpUser.apply(request)
      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe testMessage
    }

    "return userAccessDto" in {
      val mockAuthenticationService = mock[AuthenticationService]
      mockAuthenticationService.signUpUser(*)
        .returns(Future.successful((UserAccessDTO("test@mail.com", "test", None, None, Some("test")), None)))

      val authenticationController = new AuthenticationController(cc, mockAuthenticationService)
      val request = FakeRequest().withHeaders(CONTENT_TYPE -> "application/json").withBody(Json.parse(
        """{"address" : "test@mail.com", "password": "test"} """))
      val result = authenticationController.signUpUser.apply(request)
      status(result) mustBe OK
      contentAsJson(result) mustBe Json.parse(
        """ {
              |"address": "test@mail.com",
              |"password": "test",
              |"token": "test"
              |} """.stripMargin)
    }
  }
  //endregion

  //region AuthenticationController#signInUser
  "AuthenticationController#signInUser" should {

    "point out bad Json request" in {
      val mockAuthenticationService = mock[AuthenticationService]
      val authenticationController = new AuthenticationController(cc, mockAuthenticationService)
      val request = FakeRequest().withHeaders(CONTENT_TYPE -> "application/json").withBody(Json.parse(
        """{"address" : "inmproper address", "password": "test"} """))
      val result = authenticationController.signInUser.apply(request)
      status(result) mustBe BAD_REQUEST
    }

    "transmit the service error message" in {
      val mockAuthenticationService = mock[AuthenticationService]
      mockAuthenticationService.signInUser(*)
        .returns(Future.successful((UserAccessDTO("", "", None, None, None), Some(testMessage))))

      val authenticationController = new AuthenticationController(cc, mockAuthenticationService)
      val request = FakeRequest().withHeaders(CONTENT_TYPE -> "application/json").withBody(Json.parse(
        """{"address" : "test@mail.com", "password": "test"} """))
      val result = authenticationController.signInUser.apply(request)
      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe testMessage
    }

    "return userAccessDto" in {
      val mockAuthenticationService = mock[AuthenticationService]
      mockAuthenticationService.signInUser(*)
        .returns(Future.successful((UserAccessDTO("test@mail.com", "test", None, None, Some("test")), None)))

      val authenticationController = new AuthenticationController(cc, mockAuthenticationService)
      val request = FakeRequest().withHeaders(CONTENT_TYPE -> "application/json").withBody(Json.parse(
        """{"address" : "test@mail.com", "password": "test"} """))
      val result = authenticationController.signInUser.apply(request)
      status(result) mustBe OK
      contentAsJson(result) mustBe Json.parse(
        """ {
          |"address": "test@mail.com",
          |"password": "test",
          |"token": "test"
          |} """.stripMargin)
    }
  }
  //endregion

  //region ImplAuthenticatedUserAction#invokeBlock
  "ImplAuthenticatedUserAction#invokeBlock" should {

    "point out missing Token" in {
      val mockAuthenticationService = mock[AuthenticationService]
      val authenticatedUserAction = new ImplAuthenticatedUserAction(mockAuthenticationService)
      val result = authenticatedUserAction.async(
        authenticatedUser => Future.successful(Ok)).apply(FakeRequest())
      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe tokenNotFound
    }

    "transmit the service error message" in {
      val mockAuthenticationService = mock[AuthenticationService]
      mockAuthenticationService.validateToken(*)
        .returns(Future.successful(Left(testMessage)))

      val authenticatedUserAction = new ImplAuthenticatedUserAction(mockAuthenticationService)
      val result = authenticatedUserAction.async(
        authenticatedUser => Future.successful(Ok)).apply(FakeRequest().withHeaders("token" -> "test"))
      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe testMessage
    }

    "forward to block" in {
      val mockAuthenticationService = mock[AuthenticationService]
      mockAuthenticationService.validateToken(*)
        .returns(Future.successful(Right("test")))

      val authenticatedUserAction = new ImplAuthenticatedUserAction(mockAuthenticationService)
      val result = authenticatedUserAction.invokeBlock[AnyContent](
        FakeRequest().withHeaders("token" -> "test"),
        authenticatedUser => Future.successful(Ok(authenticatedUser.userId)))
      status(result) mustBe OK
      contentAsString(result) mustBe "test"
    }

  }
  //endregion

}
