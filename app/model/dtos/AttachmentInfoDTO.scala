package model.dtos

import play.api.libs.json.{ Json, OWrites }
import repositories.dtos.AttachmentInfo

case class AttachmentInfoDTO(attachmentId: String, filename: String)

object AttachmentInfoDTO {
  implicit val attachmentInfoWrites: OWrites[AttachmentInfoDTO] = Json.writes[AttachmentInfoDTO]

  def toAttachmentInfoDTO(attachmentInfo: AttachmentInfo): AttachmentInfoDTO = {
    AttachmentInfoDTO(attachmentInfo.attachmentId, attachmentInfo.filename)
  }
}