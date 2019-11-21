package services

import java.nio.file.Paths

import javax.inject.Inject
import model.dtos._
import model.dtos.EmailDTO._
import model.types._
import repositories.ChatsRepository
import PostOverseerDTO._
import OversightDTO._
import ChatOverseeingDTO._
import ChatOverseenDTO._
import ChatPreviewDTO._
import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, IOResult }
import akka.stream.scaladsl.{ FileIO, Sink, Source }
import play.api.Configuration
import CreateChatDTO._
import PatchChatDTO._
import EmailDTO._
import ChatDTO._
import org.slf4j.MDC
import play.api.Logger
import controllers.AuthenticatedUser
import play.api.mvc.AnyContent
import akka.util.ByteString
import repositories.RepUtils.RepMessages._
import utils.Jsons._
import utils.Generators.newUUID
import utils.LogMessages._

import scala.concurrent.{ ExecutionContext, Future }

class ChatService @Inject() (implicit val ec: ExecutionContext, chatsRep: ChatsRepository, config: Configuration) {
  implicit val sys: ActorSystem = ActorSystem("ChatService")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  private val log = Logger(this.getClass)

  def getChats(mailbox: Mailbox, page: Page, perPage: PerPage, sort: Sort,
    auth: AuthenticatedUser[AnyContent]): Future[Option[(Seq[ChatPreviewDTO], Int, Page)]] = {
    val userId = auth.userId
    MDC.put("serviceMethod", "getChats")
    log.info(logRequest(logGetChats))
    log.debug(logRequest(s"$logGetChats: mailbox=${mailbox.value}, page=${page.value}, perPage=${
      perPage.value
    }, sort=$sort, userId=$userId"))
    chatsRep.getChatsPreview(mailbox, page.value, perPage.value, sort.orderBy, userId)
      .map {
        case Some((chatsPreview, totalCount, lastPage)) =>
          log.info(logRepData("chats"))
          log.debug(s"${logRepData("chats")}: chatsPreview=$chatsPreview," +
            s" totalCount=$totalCount, lastPage=$lastPage")
          MDC.remove("serviceMethod")
          Some(toSeqChatPreviewDTO(chatsPreview, auth), totalCount, Page(lastPage))
        case None =>
          log.info(repReturn(logNonePagError))
          log.debug(serviceReturn(s"$logNonePagError: page=$page, perPage=$perPage"))
          MDC.remove("serviceMethod")
          None
      }
  }

  def getOverseers(chatId: String, page: Page, perPage: PerPage, sort: Sort,
    userId: String): Future[Either[Error, (Seq[PostOverseerDTO], Int, Page)]] = {
    MDC.put("serviceMethod", "getOverseers")
    log.info(logRequest(logGetOverseers))
    log.debug(logRequest(s"$logGetOverseers: chatId=$chatId, page=${page.value}, perPage=${perPage.value}," +
      s" sort=$sort, userId=$userId"))
    chatsRep.getOverseers(chatId, page.value, perPage.value, sort.orderBy, userId).map {
      case Right((postOverseers, totalCount, lastPage)) =>
        log.info(logRepData("overseers"))
        log.debug(s"${logRepData("overseers")}: chatId=$chatId, totalCount=$totalCount, lastPage=$lastPage")
        MDC.remove("serviceMethod")
        Right((toSeqPostOverseerDTO(postOverseers), totalCount, Page(lastPage)))
      case Left(`CHAT_NOT_FOUND`) =>
        log.info(repReturn(CHAT_NOT_FOUND))
        MDC.remove("serviceMethod")
        Left(chatNotFound)
      case Left(_) =>
        log.error(s"${repReturn(INVALID_PAGINATION)}. $paginationError")
        MDC.remove("serviceMethod")
        Left(internalError)
    }
  }

  def getChat(chatId: String, page: Page, perPage: PerPage, sort: Sort,
    auth: AuthenticatedUser[Any]): Future[Either[Error, (ChatDTO, Int, Page)]] = {
    val userId = auth.userId
    MDC.put("serviceMethod", "getChat")
    log.info(logRequest(logGetChat))
    log.debug(logRequest(s"$logGetChat: chatId=$chatId, page=${page.value}, perPage=${perPage.value}, sort=$sort," +
      s" userId=$userId"))
    chatsRep.getChat(chatId, page.value, perPage.value, sort.orderBy, userId).map {
      case Right((chat, totalCount, lastPage)) =>
        log.info(logRepData("chat"))
        log.debug(s"${logRepData("chat")}: chat=$chat, totalCount=$totalCount, lastPage=$lastPage")
        MDC.remove("serviceMethod")
        Right((toChatDTO(chat, auth), totalCount, Page(lastPage)))
      case Left(`CHAT_NOT_FOUND`) =>
        log.info(repReturn(CHAT_NOT_FOUND))
        MDC.remove("serviceMethod")
        Left(chatNotFound)
      case Left(_) =>
        log.error(s"${repReturn(INVALID_PAGINATION)}. $paginationError")
        MDC.remove("serviceMethod")
        Left(internalError)
    }
  }

  def postChat(createChatDTO: CreateChatDTO, userId: String): Future[Option[CreateChatDTO]] = {
    MDC.put("serviceMethod", "postChat")
    log.info(logRequest(logPostChat))
    log.debug(logRequest(s"$logPostChat: createChatDTO=$createChatDTO, userId=$userId"))
    chatsRep.postChat(CreateChatDTO.toCreateChat(createChatDTO), userId).map {
      case None =>
        log.info(repReturn("None"))
        MDC.remove("serviceMethod")
        None
      case Some(createChat) =>
        log.info(logRepData("CreateChat"))
        log.debug(s"${logRepData("CreateChat")}: chatToPost:$createChatDTO, userId=$userId, postedChat:$createChat")
        MDC.remove("serviceMethod")
        Some(toCreateChatDTO(createChat))
    }
  }

  def postEmail(upsertEmailDTO: UpsertEmailDTO, chatId: String, userId: String): Future[Option[CreateChatDTO]] = {
    MDC.put("serviceMethod", "postEmail")
    log.info(logRequest(logPostEmail))
    log.debug(logRequest(s"$logPostEmail: upsertEmailDTO=$upsertEmailDTO, chatId=$chatId, userId=$userId"))
    chatsRep.postEmail(UpsertEmailDTO.toUpsertEmail(upsertEmailDTO), chatId, userId)
      .map {
        case None =>
          log.info(repReturn("None"))
          MDC.remove("serviceMethod")
          None
        case Some(createChat) =>
          log.info(logRepData("CreateChat"))
          log.debug(s"${logRepData("CreateChat")}: emailToPost:$upsertEmailDTO, chatId=$chatId, userId=$userId," +
            s" postedEmail:$createChat")
          MDC.remove("serviceMethod")
          Some(toCreateChatDTO(createChat))
      }
  }

  def patchEmail(upsertEmailDTO: UpsertEmailDTO, chatId: String, emailId: String,
    auth: AuthenticatedUser[Any]): Future[Option[EmailDTO]] = {
    val userId = auth.userId
    MDC.put("serviceMethod", "patchEmail")
    log.info(logRequest(logPatchEmail))
    log.debug(logRequest(s"$logPatchEmail: upsertEmailDTO=$upsertEmailDTO, chatId=$chatId, emailId=$emailId," +
      s" userId=$userId"))
    chatsRep.patchEmail(UpsertEmailDTO.toUpsertEmail(upsertEmailDTO), chatId, emailId, userId)
      .map {
        case None =>
          log.info(repReturn("None"))
          MDC.remove("serviceMethod")
          None
        case Some(email) =>
          log.info(logRepData("Email"))
          log.debug(s"${logRepData("Email")}: emailToPatch:$upsertEmailDTO, chatId=$chatId, emailId=$emailId," +
            s" userId=$userId, patchedEmail:$email")
          MDC.remove("serviceMethod")
          toEmailDTO(chatId, Some(email), auth)
      }
  }

  def patchChat(patchChatDTO: PatchChatDTO, chatId: String, userId: String): Future[Option[PatchChatDTO]] = {
    MDC.put("serviceMethod", "patchChat")
    log.info(logRequest(logPatchChat))
    log.debug(logRequest(s"$logPatchChat: patchChatDTO=$patchChatDTO, chatId=$chatId, userId=$userId"))
    chatsRep.patchChat(PatchChatDTO.toPatchChat(patchChatDTO), chatId, userId).map {
      case None =>
        log.info(repReturn("None"))
        MDC.remove("serviceMethod")
        None
      case Some(patchChat) =>
        log.info(logRepData("patchChat"))
        log.debug(s"${logRepData("patchChat")}: chatToPatch:$patchChatDTO, userId=$userId, patchedChat:$patchChat")
        MDC.remove("serviceMethod")
        Some(toPatchChatDTO(patchChat))
    }
  }

  def deleteChat(chatId: String, userId: String): Future[Boolean] = {
    MDC.put("serviceMethod", "deleteChat")
    log.info(logRequest(logDeleteChat))
    log.debug(logRequest(s"$logDeleteChat: chatId=$chatId, userId=$userId"))
    chatsRep.deleteChat(chatId, userId).map {
      case false =>
        log.info(repReturn("false"))
        MDC.remove("serviceMethod")
        false
      case true =>
        log.info(repReturn("true"))
        MDC.remove("serviceMethod")
        true
    }
  }

  def deleteDraft(chatId: String, emailId: String, userId: String): Future[Boolean] = {
    MDC.put("serviceMethod", "deleteDraft")
    log.info(logRequest(logDeleteDraft))
    log.debug(logRequest(s"$logDeleteDraft: userId=$userId, chatId=$chatId, emailId=$emailId"))

    chatsRep.deleteDraft(chatId, emailId, userId).map {
      case false =>
        log.info(repReturn("false"))
        MDC.remove("serviceMethod")
        false
      case true =>
        log.info(repReturn("true"))
        MDC.remove("serviceMethod")
        true
    }
  }

  def getEmail(chatId: String, emailId: String, auth: AuthenticatedUser[Any]): Future[Option[ChatDTO]] = {
    val userId = auth.userId
    MDC.put("serviceMethod", "getEmail")
    log.info(logRequest(logGetEmail))
    log.debug(logRequest(s"$logGetEmail: chatId=$chatId, emailId=$emailId, userId=$userId"))
    chatsRep.getEmail(chatId, emailId, userId).map {
      case None =>
        log.info(repReturn("None"))
        MDC.remove("serviceMethod")
        None
      case Some(chat) =>
        log.info(logRepData("Email"))
        log.debug(s"${logRepData("Email")}: email:$chat, chatId=$chatId, userId=$userId")
        MDC.remove("serviceMethod")
        Some(toChatDTO(chat, auth))
    }
  }

  def postOverseers(postOverseersDTO: Set[PostOverseerDTO], chatId: String,
    userId: String): Future[Option[Set[PostOverseerDTO]]] = {
    MDC.put("serviceMethod", "postOverseers")
    log.info(logRequest(logPostOverseers))
    log.debug(logRequest(s"$logPostOverseers: postOverseersDTO=$postOverseersDTO, chatId=$chatId, userId=$userId"))
    chatsRep.postOverseers(toSetPostOverseer(postOverseersDTO), chatId, userId)
      .map {
        case None =>
          log.info(repReturn("None"))
          MDC.remove("serviceMethod")
          None
        case Some(setPostOverseer) =>
          log.info(logRepData("Set of PostOverseer"))
          log.debug(s"${logRepData("Set of PostOverseer")}: overseersToPost:$postOverseersDTO, chatId=$chatId," +
            s" userId=$userId, postedOverseers:$setPostOverseer")
          MDC.remove("serviceMethod")
          Some(toSetPostOverseerDTO(setPostOverseer))
      }
  }

  def deleteOverseer(chatId: String, oversightId: String, userId: String): Future[Boolean] = {
    MDC.put("serviceMethod", "deleteOverseer")
    log.info(logRequest(logDeleteOverseer))
    log.debug(logRequest(s"$logDeleteOverseer: userId=$userId, chatId=$chatId, oversightId=$oversightId"))
    chatsRep.deleteOverseer(chatId, oversightId, userId).map {
      case false =>
        log.info(repReturn("false"))
        MDC.remove("serviceMethod")
        false
      case true =>
        log.info(repReturn("true"))
        MDC.remove("serviceMethod")
        true
    }
  }

  def getOversights(userId: String): Future[Option[OversightDTO]] = {
    MDC.put("serviceMethod", "getOversights")
    log.info(logRequest(logGetOversights))
    log.debug(logRequest(s"$logGetOversights: userId=$userId"))
    chatsRep.getOversights(userId)
      .map {
        case Some(oversight) =>
          log.info(logRepData("oversight"))
          log.debug(s"${logRepData("oversight")}: oversight=$oversight, userId=$userId")
          MDC.remove("serviceMethod")
          Some(toOversightDTO(oversight))
        case None =>
          log.info(repReturn("None"))
          log.debug(s"${repReturn("None")}: userId=$userId")
          MDC.remove("serviceMethod")
          None
      }
  }

  def getOverseeings(page: Page, perPage: PerPage, sort: Sort,
    userId: String): Future[Option[(Seq[ChatOverseeingDTO], Int, Page)]] = {
    MDC.put("serviceMethod", "getOverseeings")
    log.info(logRequest(logGetOverseeings))
    log.debug(logRequest(s"$logGetOverseeings: page=${page.value}, perPage=${perPage.value}," +
      s" sort=$sort, userId=$userId"))

    chatsRep.getOverseeings(page.value, perPage.value, sort.orderBy, userId)
      .map {
        case Some((seqChatOverseeing, totalCount, lastPage)) =>
          log.info(logRepData("overseeings"))
          log.debug(s"${logRepData("overseeings")}: overseeings=$seqChatOverseeing," +
            s" totalCount=$totalCount, lastPage=$lastPage")
          MDC.remove("serviceMethod")
          Some(toSeqChatOverseeingDTO(seqChatOverseeing), totalCount, Page(lastPage))
        case None =>
          log.info(repReturn(logNonePagError))
          log.debug(serviceReturn(s"$logNonePagError: page=$page, perPage=$perPage"))
          MDC.remove("serviceMethod")
          None
      }
  }

  def getOverseens(page: Page, perPage: PerPage, sort: Sort,
    userId: String): Future[Option[(Seq[ChatOverseenDTO], Int, Page)]] = {
    MDC.put("serviceMethod", "getOverseens")
    log.info(logRequest(logGetOverseens))
    log.debug(logRequest(s"$logGetOverseens: page=${page.value}, perPage=${perPage.value}," +
      s" sort=$sort, userId=$userId"))
    chatsRep.getOverseens(page.value, perPage.value, sort.orderBy, userId)
      .map {
        case Some((seqChatOverseen, totalCount, lastPage)) =>
          log.info(logRepData("overseens"))
          log.debug(s"${logRepData("overseens")}: overseens=$seqChatOverseen," +
            s" totalCount=$totalCount, lastPage=$lastPage")
          MDC.remove("serviceMethod")
          Some(toSeqChatOverseenDTO(seqChatOverseen), totalCount, Page(lastPage))
        case None =>
          log.info(repReturn(logNonePagError))
          log.debug(repReturn(s"$logNonePagError: page=$page, perPage=$perPage"))
          MDC.remove("serviceMethod")
          None
      }
  }

  def postAttachment(chatId: String, emailId: String, userId: String, filename: String,
    source: Source[ByteString, Future[IOResult]]): Future[Option[String]] = {
    MDC.put("serviceMethod", "postAttachment")
    log.info(logRequest(logPostAttachment))
    log.debug(logRequest(s"$logPostAttachment: userId=$userId, chatId=$chatId, emailId=$emailId, filename=$filename"))
    chatsRep.verifyDraftPermissions(chatId, emailId, userId).flatMap { hasPermission =>
      if (hasPermission) {
        log.info(uploadPermissionGranted)
        for {
          optionAttachmentPath <- uploadAttachment(source)
          optionAttachmentId <- optionAttachmentPath match {
            case Some(attachmentPath) =>
              log.info(uploadSuccessful)
              MDC.remove("serviceMethod")
              Some(chatsRep.postAttachment(chatId, emailId, userId, filename, attachmentPath))
            case None =>
              log.info(uploadUnsuccessful)
              MDC.remove("serviceMethod")
              Future.successful(None)
          }
        } yield optionAttachmentId
      } else {
        log.info(uploadPermissionDenied)
        MDC.remove("serviceMethod")
        Future.successful(None)

      }
    }
  }

  private[services] def uploadAttachment(source: Source[ByteString, Future[IOResult]]): Future[Option[String]] = {
    MDC.put("serviceMethod", "uploadAttachment")
    log.info(logRequest(logUploadAttachment))
    val uploadPathString = config.get[String]("uploadDirectory") + "\\" + newUUID

    val sink: Sink[ByteString, Future[IOResult]] = FileIO.toPath(Paths.get(uploadPathString))

    source.runWith(sink)
      .map { result =>
        if (result.wasSuccessful) {
          log.info(uploadSuccessful)
          log.debug(s"$uploadSuccessful: path=$uploadPathString")
          MDC.remove("serviceMethod")
          Some(uploadPathString)
        } else {
          log.info(uploadUnsuccessful)
          log.debug(s"$uploadUnsuccessful: path=$uploadPathString")
          MDC.remove("serviceMethod")
          None
        }
      }
  }
}