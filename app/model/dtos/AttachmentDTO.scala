package model.dtos

import java.nio.file.Paths

import akka.stream.IOResult
import akka.stream.scaladsl.{ FileIO, Source }
import akka.util.ByteString
import repositories.dtos.AttachmentLocation

import scala.concurrent.Future

case class AttachmentDTO(source: Source[ByteString, Future[IOResult]], contentType: Option[String], filename: String)

object AttachmentDTO {
  def toAttachmentDTO(attachmentLocation: AttachmentLocation): AttachmentDTO = {
    attachmentLocation match {
      case AttachmentLocation(path, contentType, filename) =>
        val source = FileIO.fromPath(Paths.get(path))
        AttachmentDTO(source, contentType, filename)
    }
  }
}
