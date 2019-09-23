package services

import java.sql.Timestamp

import play.api.mvc._
import org.mockito.scalatest.AsyncIdiomaticMockito
import org.scalatest.{ AsyncWordSpec, MustMatchers, OptionValues }
import repositories.AuthenticationRepository
import utils.Jsons._
import com.github.t3hnar.bcrypt._
import utils.Values._
import utils.TestGenerators._

import scala.concurrent.Future

class AuthenticationServiceSpec extends AsyncWordSpec with Results with AsyncIdiomaticMockito with MustMatchers
  with OptionValues {

  def getServiceAndRepMock: (AuthenticationService, AuthenticationRepository) = {
    implicit val mockAuthenticationRep: AuthenticationRepository = mock[AuthenticationRepository]
    val authenticationService = new AuthenticationService()
    (authenticationService, mockAuthenticationRep)
  }

  "AuthenticationService#signUpUser" should {

    "point out repeated user" in {
      val (authenticationService, mockAuthenticationRep) = getServiceAndRepMock
      val userAccessDTO = genUserAccessDTO.sample.value.copy(token = None)

      mockAuthenticationRep.checkUser(*)
        .returns(Future.successful(true))

      authenticationService.signUpUser(userAccessDTO).map(_ mustBe Left(repeatedUser))
    }

    "return token" in {
      val (authenticationService, mockAuthenticationRep) = getServiceAndRepMock
      val token = genUUID.sample.value
      val userAccessDTO = genUserAccessDTO.sample.value.copy(token = None)

      mockAuthenticationRep.checkUser(*)
        .returns(Future.successful(false))
      mockAuthenticationRep.signUpUser(*)
        .returns(Future.successful(token))

      authenticationService.signUpUser(userAccessDTO).map(
        _ mustBe Right(jsToken(token)))
    }
  }

  "AuthenticationService#signInUser" should {

    "point out missing address" in {
      val (authenticationService, mockAuthenticationRep) = getServiceAndRepMock
      val userAccessDTO = genUserAccessDTO.sample.value.copy(token = None)

      mockAuthenticationRep.getPassword(*)
        .returns(Future.successful(None))

      authenticationService.signInUser(userAccessDTO).map(_ mustBe Left(missingAddress))
    }

    "point out wrong password" in {
      val (authenticationService, mockAuthenticationRep) = getServiceAndRepMock
      val userAccessDTO = genUserAccessDTO.sample.value.copy(token = None)
      val password = genString.sample.value

      mockAuthenticationRep.getPassword(*)
        .returns(Future.successful(Some(password.bcrypt)))

      authenticationService.signInUser(userAccessDTO).map(_ mustBe Left(wrongPassword))
    }

    "return token" in {
      val (authenticationService, mockAuthenticationRep) = getServiceAndRepMock
      val password = genString.sample.value
      val userAccessDTO = genUserAccessDTO.sample.value.copy(
        password = password,
        token = None)
      val token = genUUID.sample.value

      mockAuthenticationRep.getPassword(*)
        .returns(Future.successful(Some(password.bcrypt)))
      mockAuthenticationRep.updateToken(*)
        .returns(Future.successful(token))

      authenticationService.signInUser(userAccessDTO).map(
        _ mustBe Right(jsToken(token)))
    }

  }

  "AuthenticationService#validateToken" should {

    "point out invalid Token" in {
      val (authenticationService, mockAuthenticationRep) = getServiceAndRepMock
      val token = genUUID.sample.value

      mockAuthenticationRep.getTokenExpirationDate(*)
        .returns(Future.successful(None))

      authenticationService.validateToken(token).map(
        _ mustBe Left(tokenNotValid))
    }

    "point out expired Token" in {
      val (authenticationService, mockAuthenticationRep) = getServiceAndRepMock
      val token = genUUID.sample.value

      mockAuthenticationRep.getTokenExpirationDate(*)
        .returns(Future.successful(Some(new Timestamp(1))))

      authenticationService.validateToken(token).map(
        _ mustBe Left(tokenExpired))
    }

    "return userId" in {
      val (authenticationService, mockAuthenticationRep) = getServiceAndRepMock
      val userId = genUUID.sample.value
      val token = genUUID.sample.value

      mockAuthenticationRep.getTokenExpirationDate(*)
        .returns(Future.successful(Some(new Timestamp(year2525))))
      mockAuthenticationRep.getUser(*)
        .returns(Future.successful(userId))

      authenticationService.validateToken(token).map(
        _ mustBe Right(userId))
    }

  }

}