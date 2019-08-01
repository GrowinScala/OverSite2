package utils

import java.util.regex.Pattern

object DataValidators {

  def isValidUUID(uuidString: String): Boolean = {
    val uuidPattern = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    uuidPattern.matcher(uuidString).matches
  }

}
