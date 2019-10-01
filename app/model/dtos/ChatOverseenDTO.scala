package model.dtos

import play.api.libs.json.{ Json, OFormat }
import repositories.dtos.ChatOverseen
import OverseenDTO._

case class ChatOverseenDTO(chatId: String, overseens: Set[OverseenDTO])

object ChatOverseenDTO {
  implicit val ChatOverseenDtoOFormat: OFormat[ChatOverseenDTO] = Json.format[ChatOverseenDTO]

  def toChatOverseenDTO(chatOverseen: ChatOverseen): ChatOverseenDTO =
    ChatOverseenDTO(chatOverseen.chatId, chatOverseen.overseens.map(toOverseenDTO))

  def toChatOverseen(chatOverseenDTO: ChatOverseenDTO): ChatOverseen =
    ChatOverseen(chatOverseenDTO.chatId, chatOverseenDTO.overseens.map(toOverseen))
}