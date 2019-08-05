package services

import java.sql.Timestamp

import model.dtos.UserAccessDTO
import play.api.mvc._
import org.mockito.scalatest.AsyncIdiomaticMockito
import org.scalatest.{ AsyncWordSpec, MustMatchers }
import repositories.AuthenticationRepository
import utils.Jsons._
import org.mockito.Mockito.when
import com.github.t3hnar.bcrypt._
import utils.Values._

import scala.concurrent.Future

class AuthenticationServiceSpec extends AsyncWordSpec with Results with AsyncIdiomaticMockito with MustMatchers {

  "AuthenticationService#signUpUser" should {

    "point out repeated user" in {
      val mockAuthenticationRep = mock[AuthenticationRepository]
      when(mockAuthenticationRep.checkUser(*))
        .thenReturn(Future.successful(true))

      val authenticationService = new AuthenticationService(mockAuthenticationRep)
      authenticationService.signUpUser(UserAccessDTO.test).map(
        _._2 mustBe Some(repeatedUser))
    }

    "return token" in {
      val mockAuthenticationRep = mock[AuthenticationRepository]
      when(mockAuthenticationRep.checkUser(*))
        .thenReturn(Future.successful(false))
      when(mockAuthenticationRep.signUpUser(*, *))
        .thenReturn(Future.successful("test"))

      val authenticationService = new AuthenticationService(mockAuthenticationRep)
      authenticationService.signUpUser(UserAccessDTO.test).map(
        _ mustBe (UserAccessDTO.test.copy(token = Some("test")), None))
    }
  }

  "AuthenticationService#signInUser" should {

    "point out missing address" in {
      val mockAuthenticationRep = mock[AuthenticationRepository]
      when(mockAuthenticationRep.getPassword(*))
        .thenReturn(Future.successful(None))

      val authenticationService = new AuthenticationService(mockAuthenticationRep)
      authenticationService.signInUser(UserAccessDTO.test).map(
        _._2 mustBe Some(missingAddress))
    }

    "point out wrong password" in {
      val mockAuthenticationRep = mock[AuthenticationRepository]
      when(mockAuthenticationRep.getPassword(*))
        .thenReturn(Future.successful(Some("password".bcrypt)))

      val authenticationService = new AuthenticationService(mockAuthenticationRep)
      authenticationService.signInUser(UserAccessDTO.test).map(
        _._2 mustBe Some(wrongPassword))
    }

    "return token" in {
      val mockAuthenticationRep = mock[AuthenticationRepository]
      when(mockAuthenticationRep.getPassword(*))
        .thenReturn(Future.successful(Some("test".bcrypt)))
      when(mockAuthenticationRep.updateToken(*))
        .thenReturn(Future.successful("test"))

      val authenticationService = new AuthenticationService(mockAuthenticationRep)
      authenticationService.signInUser(UserAccessDTO.test).map(
        _ mustBe (UserAccessDTO.test.copy(token = Some("test")), None))
    }

  }

  "AuthenticationService#validateToken" should {

    "point out invalid Token" in {
      val mockAuthenticationRep = mock[AuthenticationRepository]
      when(mockAuthenticationRep.getTokenExpirationDate(*))
        .thenReturn(Future.successful(None))

      val authenticationService = new AuthenticationService(mockAuthenticationRep)
      authenticationService.validateToken("test").map(
        _ mustBe Left(tokenNotValid))
    }

    "point out expired Token" in {
      val mockAuthenticationRep = mock[AuthenticationRepository]
      when(mockAuthenticationRep.getTokenExpirationDate(*))
        .thenReturn(Future.successful(Some(new Timestamp(1))))

      val authenticationService = new AuthenticationService(mockAuthenticationRep)
      authenticationService.validateToken("test").map(
        _ mustBe Left(tokenExpired))
    }

    "return userId" in {
      val mockAuthenticationRep = mock[AuthenticationRepository]
      when(mockAuthenticationRep.getTokenExpirationDate(*))
        .thenReturn(Future.successful(Some(new Timestamp(year2525))))
      when(mockAuthenticationRep.getUser(*))
        .thenReturn(Future.successful("test"))

      val authenticationService = new AuthenticationService(mockAuthenticationRep)
      authenticationService.validateToken("test").map(
        _ mustBe Right("test"))
    }

  }

}