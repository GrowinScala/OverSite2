package utils

import play.api.libs.json._

object Jsons {

  type JsToken = JsObject

  type Error = JsValue

  def jsToken(token: String): JsToken =
    JsObject(Seq("token" -> JsString(token)))

  val repeatedUser: Error = Json.parse("""{ "Error": "User already exists"}  """)
  val failedSignIn: Error = Json.parse("""{ "Error": "Failed to Sign-In with the given address and password"}  """)
  val tokenNotFound: Error = Json.parse("""{ "Error": "Authentication Token not found in request headers"}  """)
  val tokenNotValid: Error = Json.parse("""{ "Error": "Invalid Authentication Token"}  """)
  val tokenExpired: Error = Json.parse("""{ "Error": "Authentication Token has expired, please sign-in again"}  """)
  val chatNotFound: Error = Json.parse("""{ "Error": "The given chat was not found"}  """)
  val emailNotFound: Error = Json.parse("""{ "Error": "The given email was not found"}  """)
  val cannotBothDeleteAndRestore: Error =
    Json.parse("""{ "Error": "Cannot move chat to trash and restore chat simultaneously"}""")

}
