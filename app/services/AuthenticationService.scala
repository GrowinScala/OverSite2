package services

import javax.inject.Inject
import model.dtos.PasswordDTO
import repositories.AuthenticationRepository
import com.github.t3hnar.bcrypt._

class AuthenticationService @Inject() (authenticationRep: AuthenticationRepository) {

  def insertPassword(passwordDTO: PasswordDTO) = {
    val encryptedPassword = passwordDTO.password.bcrypt
  }

}
