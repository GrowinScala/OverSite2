package utils

import play.api.libs.json.{ JsPath, JsValue, JsonValidationError }
import play.api.mvc.Request

object LogMessages {

  def logRequest(string: String) =
    s"Received a request to $string"

  val requestSignUp: String = logRequest("sign-Up User")

  val requestSignIn: String = logRequest("sign-In User")

  def requestSignUp(address: String): String = requestSignUp + s": $address"

  def serviceReturn(json: JsValue) =
    s"The Service returned: $json"

  def serviceReturn(string: String) =
    s"The Service returned $string"

  def repReturn(string: String) =
    s"""The Repository returned: "$string""""

  def badRequest(json: JsValue) =
    s"A bad request was made: ${json.toString}"

  def receivedJson(request: Request[JsValue]) =
    s"Received in request the Json: ${request.body.toString}"

  def invalidJson(DTO: Object, errors: Seq[(JsPath, Seq[JsonValidationError])]): String =
    s"Received Json did not correspond to ${DTO.getClass.getSimpleName}: ${errors.toString}"

  def invalidJsonSet(DTO: Object, errors: Seq[(JsPath, Seq[JsonValidationError])]): String =
    s"Received Json did not correspond to a set of ${DTO.getClass.getSimpleName}: ${errors.toString}"

  def invalidJson(DTO: Object): String =
    s"Received Json did not correspond to ${DTO.getClass.getSimpleName}"

  def invalidJsonSet(DTO: Object): String =
    s"Received Json did not correspond to a set of ${DTO.getClass.getSimpleName}"

  def repToken(token: String) =
    s"Received token from Repository: $token"

  val paginationError = "This implies the binders incorrectly approved the pagination"

  def paginatedResult(resultType: String, result: Object, totalCount: Int, lastPage: Int, page: Int,
    perPage: Int): String =
    s"Retrieved the $resultType: $resultType: $result, page=$page, perPage=$perPage totalCount=$totalCount," +
      s" lastPage=$lastPage"

  val logNoneAuthError = """"None". This means the Authentication incorrectly approved this user"""

  val logNonePagError = """"None". This means the Binders incorrectly approved the pagination"""

  val logGetChat = "get a chat"

  val logGetEmail = "get an email"

  val logGetChats = "get a preview of the chats"

  val logGetOverseers = "get a user's overseers of a chat"

  val logPostChat = "post a chat"

  val logPostEmail = "post an email"

  val logPatchEmail = "patch an email"

  val logPatchChat = "patch a chat"

  val logDeleteChat = "delete a chat"

  val logDeleteDraft = "delete a draft"

  val logDeleteOverseer = "delete a overseer"

  val logPostOverseers = "post a set of overseers"

  val logGetOversights = "get a preview of the oversights"

  val logGetOverseeings = "get a user's overseeings"

  val logGetOverseens = "get a user's overseens"

  def logRepData(dataType: String) = s"Received the $dataType data from the repository"

}
