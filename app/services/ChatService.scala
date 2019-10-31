package services

import java.io.File
import java.nio.file.{ Files, Paths }

import javax.inject.Inject
import model.dtos._
import model.types._
import repositories.ChatsRepository
import PostOverseerDTO._
import OversightDTO._
import ChatOverseeingDTO._
import ChatOverseenDTO._
import ChatPreviewDTO._
import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, IOResult }
import akka.stream.scaladsl.FileIO
import play.api.Configuration
import ChatDTO._
import repositories.RepUtils.RepMessages._
import utils.Jsons._

import scala.concurrent.{ ExecutionContext, Future }

class ChatService @Inject() (implicit val ec: ExecutionContext, chatsRep: ChatsRepository, config: Configuration) {
  implicit val sys = ActorSystem("ChatService")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  def getChats(mailbox: Mailbox, page: Page, perPage: PerPage, sort: Sort,
    userId: String): Future[Option[(Seq[ChatPreviewDTO], Int, Page)]] =
    chatsRep.getChatsPreview(mailbox, page.value, perPage.value, sort.orderBy, userId)
      .map(_.map {
        case (chatsPreview, totalCount, lastPage) =>
          (toSeqChatPreviewDTO(chatsPreview), totalCount, Page(lastPage))
      })

  def getOverseers(chatId: String, page: Page, perPage: PerPage, sort: Sort,
    userId: String): Future[Either[Error, (Seq[PostOverseerDTO], Int, Page)]] =

    chatsRep.getOverseers(chatId, page.value, perPage.value, sort.orderBy, userId).map {
      case Right((postOverseers, totalCount, lastPage)) =>
        Right((toSeqPostOverseerDTO(postOverseers), totalCount, Page(lastPage)))
      case Left(`CHAT_NOT_FOUND`) => Left(chatNotFound)
      case Left(_) => Left(internalError)
    }

  def getChat(chatId: String, page: Page, perPage: PerPage, sort: Sort,
    userId: String): Future[Either[Error, (ChatDTO, Int, Page)]] =
    chatsRep.getChat(chatId, page.value, perPage.value, sort.orderBy, userId).map {
      case Right((chat, totalCount, lastPage)) =>
        Right((toChatDTO(chat), totalCount, Page(lastPage)))
      case Left(`CHAT_NOT_FOUND`) => Left(chatNotFound)
      case Left(_) => Left(internalError)
    }

  def postChat(createChatDTO: CreateChatDTO, userId: String): Future[Option[CreateChatDTO]] = {
    chatsRep.postChat(CreateChatDTO.toCreateChat(createChatDTO), userId).map(_.map(CreateChatDTO.toCreateChatDTO))
  }

  def postEmail(upsertEmailDTO: UpsertEmailDTO, chatId: String, userId: String): Future[Option[CreateChatDTO]] = {
    chatsRep.postEmail(UpsertEmailDTO.toUpsertEmail(upsertEmailDTO), chatId, userId)
      .map(_.map(CreateChatDTO.toCreateChatDTO))
  }

  def patchEmail(upsertEmailDTO: UpsertEmailDTO, chatId: String, emailId: String,
    userId: String): Future[Option[EmailDTO]] = {
    chatsRep.patchEmail(UpsertEmailDTO.toUpsertEmail(upsertEmailDTO), chatId, emailId, userId)
      .map(EmailDTO.toEmailDTO)
  }

  def patchChat(patchChatDTO: PatchChatDTO, chatId: String, userId: String): Future[Option[PatchChatDTO]] = {
    chatsRep.patchChat(PatchChatDTO.toPatchChat(patchChatDTO), chatId, userId).map(_.map(PatchChatDTO.toPatchChatDTO))
  }

  def deleteChat(chatId: String, userId: String): Future[Boolean] = {
    chatsRep.deleteChat(chatId, userId)
  }

  def deleteDraft(chatId: String, emailId: String, userId: String): Future[Boolean] = {
    chatsRep.deleteDraft(chatId, emailId, userId)
  }

  def getEmail(chatId: String, emailId: String, userId: String): Future[Option[ChatDTO]] = {
    chatsRep.getEmail(chatId, emailId, userId).map(_.map(ChatDTO.toChatDTO))
  }

  def postOverseers(postOverseersDTO: Set[PostOverseerDTO], chatId: String,
    userId: String): Future[Option[Set[PostOverseerDTO]]] =
    chatsRep.postOverseers(toSetPostOverseer(postOverseersDTO), chatId, userId)
      .map(_.map(toSetPostOverseerDTO))

  def deleteOverseer(chatId: String, oversightId: String, userId: String): Future[Boolean] =
    chatsRep.deleteOverseer(chatId, oversightId, userId)

  def getOversights(userId: String): Future[Option[OversightDTO]] =
    chatsRep.getOversights(userId)
      .map(_.map(toOversightDTO))

  def getOverseeings(page: Page, perPage: PerPage, sort: Sort,
    userId: String): Future[Option[(Seq[ChatOverseeingDTO], Int, Page)]] =
    chatsRep.getOverseeings(page.value, perPage.value, sort.orderBy, userId)
      .map(_.map {
        case (seqChatOverseeing, totalCount, lastPage) =>
          (toSeqChatOverseeingDTO(seqChatOverseeing), totalCount, Page(lastPage))
      })

  def getOverseens(page: Page, perPage: PerPage, sort: Sort,
    userId: String): Future[Option[(Seq[ChatOverseenDTO], Int, Page)]] =
    chatsRep.getOverseens(page.value, perPage.value, sort.orderBy, userId)
      .map(_.map {
        case (seqChatOverseeing, totalCount, lastPage) =>
          (toSeqChatOverseenDTO(seqChatOverseeing), totalCount, Page(lastPage))
      })

  def postAttachment(chatId: String, emailId: String, userId: String, filename: String, file: File): Future[Option[String]] = {
    chatsRep.verifyDraftPermissions(chatId, emailId, userId).flatMap { hasPermission =>
      if (hasPermission) {
        for {
          optionAttachmentPath <- uploadAttachment(file)
          optionAttachmentId <- optionAttachmentPath match {
            case Some(attachmentPath) => chatsRep.postAttachment(chatId, emailId, userId, filename, attachmentPath)
            case None => Future.successful(None)
          }
        } yield optionAttachmentId
      } else Future.successful(None)
    }
  }

  private def uploadAttachment(file: File): Future[Option[String]] = {
    val filePath = file.toPath
    val uploadPathString = config.get[String]("uploadDirectory") + "\\" + filePath.getFileName

    FileIO.fromPath(filePath)
      .to(FileIO.toPath(Paths.get(uploadPathString)))
      .run()
      .map { result =>
        if (result.wasSuccessful) {
          Files.deleteIfExists(filePath)
          Some(uploadPathString)
        } else None
      }
  }
}