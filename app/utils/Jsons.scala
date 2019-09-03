package utils

import play.api.libs.json.{ JsValue, Json }

object Jsons {

  val repeatedUser: JsValue = Json.parse("""{ "Error": "User already exists"}  """)
  val missingAddress: JsValue = Json.parse("""{ "Error": "User address not found"}  """)
  val wrongPassword: JsValue = Json.parse("""{ "Error": "User password is incorrect"}  """)
  val tokenNotFound: JsValue = Json.parse("""{ "Error": "Authentication Token not found in request headers"}  """)
  val tokenNotValid: JsValue = Json.parse("""{ "Error": "Invalid Authentication Token"}  """)
  val tokenExpired: JsValue = Json.parse("""{ "Error": "Authentication Token has expired, please sign-in again"}  """)
  val chatNotFound: JsValue = Json.parse("""{ "Error": "The given chat was not found"}  """)
  val emailNotFound: JsValue = Json.parse("""{ "Error": "The given email was not found"}  """)

}
