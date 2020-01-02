package repositories.slick.implementations

import java.math._

import com.google.common.math.IntMath._
import javax.inject.Inject
import repositories.RepUtils.RepConstants._
import model.types.{ Mailbox, ParticipantType }
import model.types.Mailbox._
import model.types.ParticipantType._
import org.slf4j.MDC
import play.api.Logger
import repositories.ChatsRepository
import repositories.dtos.PatchChat.{ ChangeSubject, MoveToTrash, Restore }
import repositories.slick.mappings._
import repositories.dtos._
import slick.dbio.{ DBIOAction, Effect }
import slick.jdbc.MySQLProfile.api._
import utils.DateUtils
import utils.Generators._
import repositories.RepUtils.RepMessages._
import repositories.RepUtils.types.OrderBy
import repositories.RepUtils.types.OrderBy._

import math._
import scala.concurrent._
import repositories.slick.mappings.EmailAddressesTable._
import utils.LogMessages._

import scala.collection.immutable
import scala.concurrent.duration.Duration

class SlickChatsRepository @Inject() (db: Database)(implicit executionContext: ExecutionContext)
  extends ChatsRepository {

  private val log = Logger(this.getClass)
  val PREVIEW_BODY_LENGTH: Int = 30

  //region Shared auxiliary methods
  /**
   * This query returns, for a user, all of its chats and for EACH chat, all of its emails
   * (without body) and for each email, all of its participants
   *
   * @param userId Id of the user
   * @param optBox Optional Mailbox specification. If used, a chat will only be shown if the user has it inside
   *               the specified mailbox.
   * @return Query: For a user, all of its chats and for EACH chat, all of its emails
   *         (without body) and for each email, all of its participants
   */
  private[implementations] def getChatsMetadataQueryByUserId(userId: String, optBox: Option[Mailbox] = None) = {
    for {
      chatId <- UserChatsTable.all.filter(userChatRow =>
        userChatRow.userId === userId &&
          (optBox match {
            case Some(Inbox) => userChatRow.inbox === 1
            case Some(Sent) => userChatRow.sent === 1
            case Some(Trash) => userChatRow.trash === 1
            case Some(Drafts) => userChatRow.draft >= 1
            case None => true
          })).map(_.chatId)

      (emailId, body, date, sent) <- EmailsTable.all.filter(_.chatId === chatId).map(
        emailRow => (emailRow.emailId, emailRow.body, emailRow.date, emailRow.sent))

      (addressId, participantType) <- EmailAddressesTable.all.filter(_.emailId === emailId)
        .map(emailAddressRow =>
          (emailAddressRow.addressId, emailAddressRow.participantType))

    } yield (chatId, emailId, body, date, sent, addressId, participantType)
  }

  /**
   * Method that returns all the addressIds of the oversees of a given overseer for a given chat
   *
   * @param userId The userId of the overseer
   * @param chatId The chat in question
   * @return Query addressIds of the oversees for the given chat
   */
  private def getUserChatOverseesQuery(userId: String, chatId: Rep[String]) = {
    OversightsTable.all
      .join(UsersTable.all)
      .on((oversightRow, userRow) =>
        userRow.userId === oversightRow.overseeId &&
          oversightRow.overseerId === userId &&
          oversightRow.chatId === chatId)
      .map { case (oversight, oversee) => oversee.addressId }
  }

  /**
   * Builds a Query that retrieves the emails that a specific user can see:
   *
   * @param userId The user in question
   * @param optBox Optional filter for a given mailbox
   * @return Query for the emails that a specific user can see
   *         in a tuple containing (chatId, emailId, body, date, sent):
   * - If the user is a participant of the email (from, to, bcc, cc)
   *         OR if the user is overseeing another user in the chat (has access to the same emails the oversee has,
   *         excluding the oversee's drafts)
   * - AND if email is draft (sent = 0), only the user with the From address can see it
   *
   * (chatId, emailId, body, date, sent)
   */
  private def getVisibleEmailsQuery(userId: String, optBox: Option[Mailbox] = None) =
    (for {
      userAddressId <- UsersTable.all.filter(_.userId === userId).map(_.addressId)

      (chatId, emailId, body, date, sent) <- getChatsMetadataQueryByUserId(userId, optBox).filter {
        case (chatId, emailId, body, date, sent, addressId, participantType) =>
          (addressId === userAddressId || addressId.in(getUserChatOverseesQuery(userId, chatId))) &&
            (sent === 1 || (participantType === from && addressId === userAddressId))
      }.map {
        case (chatId, emailId, body, date, sent, addressId, participantType) =>
          (chatId, emailId, body, date, sent)
      }

    } yield (chatId, emailId, body, date, sent)).distinct

  //endregion

  /**
   * Creates a DBIOAction that returns a paginated preview of all the chats of a given user in a given Mailbox
   *
   * @param mailbox The mailbox being seen
   * @param page The page being seen
   * @param perPage The number of chats per page
   * @param userId  The userId of the user in question
   * @return A DBIOAction that when run returns a tuple that contains a sliced sequence of ChatPreview dtos,
   *         the total number of chats in the full sequence and number of the last page containing elements.
   *         The preview of each chat only shows the most recent email
   */
  private[implementations] def getChatsPreviewAction(mailbox: Mailbox, page: Int, perPage: Int, orderBy: OrderBy,
    userId: String): DBIO[Option[(Seq[ChatPreview], Int, Int)]] = {
    MDC.put("repMethod", "getChatsPreviewAction")
    log.info(logRequest(logGetChats))
    log.debug(logRequest(s"$logGetChats: mailbox=$mailbox, page=$page, perPage=$perPage," +
      s" orderBy=$OrderBy, userId=$userId"))
    if (page < 0 || perPage <= 0 || perPage > MAX_PER_PAGE) {
      log.info(INVALID_PAGINATION)
      log.debug(s"$INVALID_PAGINATION. page=$page, perPage=$perPage")
      MDC.remove("repMethod")
      DBIO.successful(None)
    } else {
      val sortedChatPreviewQuery = if (orderBy == Asc) getChatsPreviewQuery(userId, Some(mailbox))
        .sortBy { case (chatId, subject, address, date, body) => (date.asc, body.asc, address.asc) }
      else getChatsPreviewQuery(userId, Some(mailbox))
        .sortBy { case (chatId, subject, address, date, body) => (date.desc, body.asc, address.asc) }

      for {
        totalCount <- sortedChatPreviewQuery.length.result
        slicedChats <- sortedChatPreviewQuery.drop(perPage * page).take(perPage).result

      } yield {
        val result = (slicedChats.map(ChatPreview.tupled), totalCount,
          divide(totalCount, perPage, RoundingMode.CEILING) - 1)
        log.info("Retrieved the paginated chats")
        log.debug(s"${
          paginatedResult("chatsPreview", result._1, totalCount = result._2, lastPage = result._3, page,
            perPage)
        }, mailbox=$mailbox, userId=$userId")
        MDC.remove("repMethod")
        Some(result)
      }
    }

  }

  /**
   * Method that returns a paginated preview of all the chats of a given user in a given Mailbox
   *
   * @param mailbox The mailbox being seen
   * @param page The page being seen
   * @param perPage The number of chats per page
   * @param userId  The userId of the user in question
   * @return A Future tuple that contains a sliced sequence of ChatPreview dtos, the total number of chats in the full
   *         sequence and number of the last page containing elements. The preview of each chat only shows the most
   *         recent email
   */
  def getChatsPreview(mailbox: Mailbox, page: Int, perPage: Int, orderBy: OrderBy,
    userId: String): Future[Option[(Seq[ChatPreview], Int, Int)]] =
    db.run(getChatsPreviewAction(mailbox, page, perPage, orderBy, userId).transactionally)

  /**
   * Creates a DBIOAction to get the paginated emails and other data of a specific chat of a user
   * @param chatId ID of the chat requested
   * @param page The page being seen
   * @param perPage The number of emails per page
   * @param userId ID of the user who requested the chat
   * @param getAll Boolean used to indicate if all emails should be returned. False by default
   * @return A DBIOAction that when run returns Either a tuple that contains a Chat DTO with a slice of the
   *         chat's emails, the total number of emails in the full sequence and number of the last page
   *         containing elements.
   *
   *         The Chat DTO carries the chat's subject,
   *         the addresses involved in the chat,
   *         the overseers of the chat
   *         and a slice of the emails of the chat according to the page and perPage values.
   *
   *         Or a String indicating what went wrong
   */
  private[implementations] def getChatAction(chatId: String, page: Int, perPage: Int, orderBy: OrderBy,
    userId: String, getAll: Boolean = false): DBIO[Either[String, (Chat, Int, Int)]] = {
    MDC.put("repMethod", "getChatAction")
    log.info(logRequest(logGetChat))
    log.debug(logRequest(s"$logGetChat: chatId=$chatId, page=$page, perPage=$perPage, orderBy=$OrderBy," +
      s" userId=$userId, getAll=$getAll"))
    if (page < 0 || perPage <= 0 || perPage > MAX_PER_PAGE) {
      log.info(INVALID_PAGINATION)
      log.debug(s"$INVALID_PAGINATION. page=$page, perPage=$perPage")
      MDC.remove("repMethod")
      DBIO.successful(Left(INVALID_PAGINATION))
    } else {
      for {
        chatData <- getChatDataAction(chatId, userId)
        (addresses, emails, totalCount) <- getGroupedEmailsAndAddresses(chatId, page, perPage, userId, orderBy, getAll)
        overseers <- getOverseersData(chatId)
      } yield chatData match {
        case None =>
          log.info(CHAT_NOT_FOUND)
          log.debug(s"$CHAT_NOT_FOUND: chatId: $chatId, userId: $userId")
          MDC.remove("repMethod")
          Left(CHAT_NOT_FOUND)
        case Some((_, subject, _)) =>
          if (getAll) {
            val result = (Chat(chatId, subject, addresses, overseers, emails), totalCount, 0)
            log.info("Retrieved the whole chat")
            log.debug(paginatedResult("chat", result._1, totalCount = result._2, lastPage = result._3, page, perPage))
            MDC.remove("repMethod")
            Right(result)
          } else {
            val result = (Chat(chatId, subject, addresses, overseers, emails), totalCount,
              divide(totalCount, perPage, RoundingMode.CEILING) - 1)
            log.info("Retrieved the paginated chat")
            log.debug(s"${
              paginatedResult("chat", result._1, totalCount = result._2, lastPage = result._3, page,
                perPage)
            }, chatId=$chatId, userId=$userId")
            MDC.remove("repMethod")
            Right(result)
          }
      }
    }
  }

  /**
   * Method to get the paginated emails and other data of a specific chat of a user
   * @param chatId ID of the chat requested
   * @param page The page being seen
   * @param perPage The number of emails per page
   * @param userId ID of the user who requested the chat
   * @return Either a tuple that contains a Chat DTO with a slice of the chat's emails, the total number of emails
   *         in the full sequence and number of the last page containing elements.
   *         The Chat DTO carries the chat's subject,
   *         the addresses involved in the chat,
   *         the overseers of the chat
   *         and a slice of the emails of the chat according to the page and perPage values.
   *
   *         Or a String indicating what went wrong
   */
  def getChat(chatId: String, page: Int, perPage: Int, orderBy: OrderBy,
    userId: String): Future[Either[String, (Chat, Int, Int)]] =
    db.run(getChatAction(chatId, page, perPage, orderBy, userId).transactionally)

  private def getOverseersAction(chatId: String, page: Int, perPage: Int, orderBy: OrderBy,
    userId: String): DBIO[Either[String, (Seq[PostOverseer], Int, Int)]] = {
    MDC.put("repMethod", "getOverseersAction")
    log.info(logRequest(logGetOverseers))
    log.debug(logRequest(s"$logGetOverseers: chatId=$chatId, page=$page, perPage=$perPage, orderBy=$OrderBy," +
      s" userId=$userId"))
    if (page < 0 || perPage <= 0 || perPage > MAX_PER_PAGE) {
      log.info(INVALID_PAGINATION)
      log.debug(s"$INVALID_PAGINATION. page=$page, perPage=$perPage")
      MDC.remove("repMethod")
      DBIO.successful(Left(INVALID_PAGINATION))
    } else {
      for {
        optChatData <- getChatDataAction(chatId, userId)
        result <- optChatData match {
          case Some(_) =>
            val orderedOverseersQuery = if (orderBy == Desc) getOverseersQuery(chatId, userId)
              .sortBy { case (address, oversightId) => (address.desc, oversightId.asc) }
            else getOverseersQuery(chatId, userId)
              .sortBy { case (address, oversightId) => (address.asc, oversightId.asc) }
            for {
              totalCount <- orderedOverseersQuery.length.result
              seqOverseers <- orderedOverseersQuery.drop(perPage * page).take(perPage).result

            } yield {
              val result = (seqOverseers.map {
                case (address, oversightId) =>
                  PostOverseer(address, Some(oversightId))
              }, totalCount, divide(totalCount, perPage, RoundingMode.CEILING) - 1)

              log.info("Retrieved the paginated overseers")
              log.debug(s"${
                paginatedResult("overseers", result._1, totalCount = result._2, lastPage = result._3, page,
                  perPage)
              }, chatId=$chatId, userId=$userId")
              Right(result)
            }

          case None =>
            log.info(CHAT_NOT_FOUND)
            log.debug(s"$CHAT_NOT_FOUND: chatId: $chatId, userId: $userId")
            DBIO.successful(Left(CHAT_NOT_FOUND))
        }
      } yield {
        MDC.remove("repMethod")
        result
      }
    }
  }

  def getOverseers(chatId: String, page: Int, perPage: Int, orderBy: OrderBy,
    userId: String): Future[Either[String, (Seq[PostOverseer], Int, Int)]] =
    db.run(getOverseersAction(chatId, page, perPage, orderBy, userId).transactionally)

  /**
   * Creates a DBIOAction that inserts a chat with an email into the database
   *
   * @param createChat The DTO that contains the Chat
   * @param userId     The Id of the User who is inserting the chat
   * @return A DBIO that returns a copy of the original createChat but with the Ids of the created chat and email
   *         as well as the emails date.
   *         If the userId does not have a corresponding address the DBIO does nothing and returns None.
   */
  private[implementations] def postChatAction(createChat: CreateChat, userId: String): DBIO[Option[CreateChat]] = {
    MDC.put("repMethod", "postChatAction")
    log.info(logRequest(logPostChat))
    log.debug(logRequest(s"$logPostChat: createChat=$createChat, userId=$userId"))
    val emailDTO = createChat.email
    val date = DateUtils.getCurrentDate

    /** Generate chatId, userChatId and emailId **/
    val chatId = newUUID
    val userChatId = newUUID
    val emailId = newUUID

    val inserts = for {
      optFromAddress <- UsersTable.all.join(AddressesTable.all)
        .on { case (user, address) => user.addressId === address.addressId && user.userId === userId }
        .map { case (usersTable, addressesTable) => addressesTable.address }.result.headOption

      _ <- optFromAddress match {
        case Some(fromAddress) => for {
          _ <- ChatsTable.all += ChatRow(chatId, createChat.subject.getOrElse(""))
          _ <- UserChatsTable.all += UserChatRow(userChatId, userId, chatId, 0, 0, 1, 0)
          _ <- insertEmailAndAddresses(emailDTO, chatId, emailId, fromAddress, date)
        } yield ()
        case None =>
          log.info(USER_NOT_FOUND)
          log.debug(s"$USER_NOT_FOUND: userId: $userId")
          DBIO.successful(())
      }

    } yield optFromAddress

    inserts.map(_.map(fromAddress => {
      val postedChat = createChat.copy(chatId = Some(chatId), email = emailDTO.copy(
        emailId = Some(emailId),
        from = Some(fromAddress), date = Some(date)))
      log.info("chat posted")
      log.debug(s"chat posted: postedChat: $postedChat, userId=$userId")
      MDC.remove("repMethod")
      postedChat
    }))
  }

  /**
   * Inserts a chat with an email into the database
   *
   * @param createChat The DTO that contains the Chat
   * @param userId     The Id of the User who is inserting the chat
   * @return A Future that contains a copy of the original createChat but with the Ids
   *         of the created chat and email as well as the emails date.
   */
  def postChat(createChat: CreateChat, userId: String): Future[Option[CreateChat]] =
    db.run(postChatAction(createChat, userId).transactionally)

  private[implementations] def postEmailAction(upsertEmail: UpsertEmail, chatId: String, userId: String): DBIO[Option[CreateChat]] = {
    MDC.put("repMethod", "postEmailAction")
    log.info(logRequest(logPostEmail))
    log.debug(logRequest(s"$logPostEmail: upsertEmail=$upsertEmail, chatId=$chatId, userId=$userId"))
    val date = DateUtils.getCurrentDate

    val emailId = newUUID

    val insertAndUpdate = for {
      chatAddress <- getChatDataAction(chatId, userId)

      _ <- chatAddress match {
        case Some((chatID, subject, fromAddress)) => insertEmailAndAddresses(upsertEmail, chatId,
          emailId, fromAddress, date)
          .andThen(UserChatsTable.incrementDrafts(userId, chatId))
        case None => DBIOAction.successful(None)
      }

    } yield chatAddress

    insertAndUpdate.map {
      case Some(tuple) =>
        val postedEmail = tuple match {
          case (chatID, subject, fromAddress) => CreateChat(
            Some(chatID),
            Some(subject),
            UpsertEmail(
              emailId = Some(emailId),
              from = Some(fromAddress),
              to = upsertEmail.to,
              bcc = upsertEmail.bcc,
              cc = upsertEmail.cc,
              body = upsertEmail.body,
              date = Some(date),
              sent = Some(false)))
        }
        log.info("email posted")
        log.debug(s"email posted: postedEmail: $postedEmail, chatId=$chatId, userId=$userId")
        MDC.remove("repMethod")
        Some(postedEmail)

      case None =>
        log.info(CHAT_NOT_FOUND)
        log.debug(s"$CHAT_NOT_FOUND: chatId=$chatId, userId=$userId")
        MDC.remove("repMethod")
        None
    }
  }

  def postEmail(upsertEmail: UpsertEmail, chatId: String, userId: String): Future[Option[CreateChat]] =
    db.run(postEmailAction(upsertEmail, chatId, userId).transactionally)

  private[implementations] def patchEmailAction(upsertEmail: UpsertEmail, chatId: String,
    emailId: String, userId: String): DBIO[Option[Email]] = {
    MDC.put("repMethod", "patchEmailAction")
    log.info(logRequest(logPatchEmail))
    log.debug(logRequest(s"$logPatchEmail: upsertEmail=$upsertEmail, chatId=$chatId, emailId=$emailId," +
      s" userId=$userId"))
    val updateAndSendEmail = for {
      updatedReceiversAddresses <- updateEmailAction(upsertEmail, chatId, emailId, userId)

      sendEmail <- DBIO.sequenceOption(
        updatedReceiversAddresses.map(receiversAddresses =>
          if (upsertEmail.sent.getOrElse(false))
            sendEmailAction(userId, chatId, emailId, receiversAddresses)
          else DBIO.successful(0)))

    } yield sendEmail

    for {
      optionPatch <- updateAndSendEmail.transactionally
      email <- getEmailDTOAction(userId, emailId)
    } yield optionPatch.flatMap(_ => email.headOption) match {
      case None =>
        log.info(EMAIL_NOT_FOUND)
        log.debug(s"$EMAIL_NOT_FOUND: chatId=$chatId, emailId=$emailId, userId=$userId")
        MDC.remove("repMethod")
        None
      case Some(patchedEmail) =>
        log.info("email patched")
        log.debug(s"email patched: patchedEmail: $patchedEmail, chatId=$chatId, emailId=$emailId, userId=$userId")
        MDC.remove("repMethod")
        Some(patchedEmail)
    }
  }

  def patchEmail(upsertEmail: UpsertEmail, chatId: String, emailId: String, userId: String): Future[Option[Email]] =
    db.run(patchEmailAction(upsertEmail, chatId, emailId, userId).transactionally)

  private[implementations] def patchChatAction(patchChat: PatchChat, chatId: String,
    userId: String): DBIO[Option[PatchChat]] = {
    MDC.put("repMethod", "patchChatAction")
    log.info(logRequest(logPatchChat))
    log.debug(logRequest(s"$logPatchChat: patchChat=$patchChat, chatId=$chatId, userId=$userId"))
    for {
      optionPatch <- patchChat match {
        case MoveToTrash => moveToTrashAction(chatId, userId)
        case Restore => tryRestoreChatAction(chatId, userId)
        case ChangeSubject(subject) => changeChatSubjectAction(chatId, userId, subject)
      }
    } yield optionPatch.map(_ => patchChat) match {
      case None =>
        log.info(CHAT_NOT_FOUND)
        log.debug(s"$CHAT_NOT_FOUND: chatId=$chatId, userId=$userId")
        MDC.remove("repMethod")
        None
      case Some(patchedChat) =>
        log.info("chat patched")
        log.debug(s"chat patched: patchedChat: $patchedChat, chatId=$chatId, userId=$userId")
        MDC.remove("repMethod")
        Some(patchedChat)
    }
  }

  def patchChat(patchChat: PatchChat, chatId: String, userId: String): Future[Option[PatchChat]] =
    db.run(patchChatAction(patchChat, chatId, userId).transactionally)

  private[implementations] def getEmailAction(chatId: String, emailId: String, userId: String): DBIO[Option[Chat]] = {
    MDC.put("repMethod", "getEmailAction")
    log.info(logRequest(logGetEmail))
    log.debug(logRequest(s"$logGetEmail: chatId=$chatId, emailId=$emailId, userId=$userId"))
    getChatAction(chatId, 0, 1, DefaultOrder, userId, getAll = true).map {
      case Left(_) =>
        log.info(CHAT_NOT_FOUND)
        log.debug(s"$CHAT_NOT_FOUND: chatId=$chatId, userId=$userId")
        MDC.remove("repMethod")
        None
      case Right((chat, _, _)) =>
        Some(chat.copy(emails = chat.emails.filter(email => email.emailId == emailId)))
          .filter(_.emails.nonEmpty) match {
            case None =>
              log.info(EMAIL_NOT_FOUND)
              log.debug(s"$EMAIL_NOT_FOUND: chatId=$chatId, emailId=$emailId, userId=$userId")
              MDC.remove("repMethod")
              None
            case Some(singleEmailChat) =>
              log.info("Retrieved email")
              log.debug(s"Retrieved email: singleEmailChat: $singleEmailChat, chatId=$chatId, emailId=$emailId," +
                s" userId=$userId")
              MDC.remove("repMethod")
              Some(singleEmailChat)
          }
    }
  }

  def getEmail(chatId: String, emailId: String, userId: String): Future[Option[Chat]] = {
    db.run(getEmailAction(chatId, emailId, userId).transactionally)
  }

  private[implementations] def deleteChatAction(chatId: String, userId: String): DBIO[Boolean] = {
    MDC.put("repMethod", "deleteChatAction")
    log.info(logRequest(logDeleteChat))
    log.debug(logRequest(s"$logDeleteChat: chatId=$chatId, userId=$userId"))
    val userChatQuery = UserChatsTable.all
      .filter(userChatRow => userChatRow.userId === userId && userChatRow.chatId === chatId && userChatRow.trash === 1)
    for {
      //User chat must exist and already be in trash to be able to delete definitely
      getUserChat <- userChatQuery.result.headOption

      updateUserChatRow <- getUserChat match {
        case Some(userChat) => userChatQuery
          .map(userChatRow => (userChatRow.inbox, userChatRow.sent, userChatRow.draft, userChatRow.trash))
          .update(0, 0, 0, 0)
        case None => DBIO.successful(0)
      }
    } yield if (updateUserChatRow > 0) {
      log.info("chat deleted")
      log.debug(s"chat deleted: chatId=$chatId, userId=$userId")
      MDC.remove("repMethod")
      true
    } else {
      log.info(CHAT_NOT_FOUND)
      log.debug(s"$CHAT_NOT_FOUND: chatId=$chatId, userId=$userId")
      MDC.remove("repMethod")
      false
    }
  }

  def deleteChat(chatId: String, userId: String): Future[Boolean] =
    db.run(deleteChatAction(chatId, userId).transactionally)

  private[implementations] def deleteDraftAction(chatId: String, emailId: String, userId: String): DBIO[Boolean] = {
    MDC.put("repMethod", "deleteDraftAction")
    log.info(logRequest(logDeleteDraft))
    log.debug(logRequest(s"$logDeleteDraft: userId=$userId, chatId=$chatId, emailId=$emailId"))

    for {
      allowedToDeleteDraft <- verifyDraftPermissionsAction(chatId, emailId, userId)

      deleted <- if (allowedToDeleteDraft) deleteDraftRowsAction(chatId, emailId, userId).transactionally
      else DBIO.successful(false)
    } yield if (deleted) {
      log.info("draft deleted")
      log.debug(s"draft deleted: chatId=$chatId, emailId=$emailId, userId=$userId")
      MDC.remove("repMethod")
      true
    } else {
      log.info(EMAIL_NOT_FOUND)
      log.debug(s"$EMAIL_NOT_FOUND: chatId=$chatId, emailId=$emailId, userId=$userId")
      MDC.remove("repMethod")
      false
    }
  }

  def deleteDraft(chatId: String, emailId: String, userId: String): Future[Boolean] = {
    db.run(deleteDraftAction(chatId, emailId, userId).transactionally)
  }

  private def postOverseersAction(postOverseers: Set[PostOverseer], chatId: String,
    userId: String): DBIO[Option[Set[PostOverseer]]] = {
    MDC.put("repMethod", "postOverseersAction")
    log.info(logRequest(logPostOverseers))
    log.debug(logRequest(s"$logPostOverseers: postOverseers=$postOverseers, chatId=$chatId, userId=$userId"))

    for {
      chatAccessAndParticipation <- checkIfUserHasAccessAndParticipates(chatId, userId)

      optSeqPostOverseer <- if (chatAccessAndParticipation)
        DBIO.sequence(postOverseers.map(postOverseerAction(_, chatId, userId)).toSeq)
          .map(Some(_))
      else DBIO.successful(None)

    } yield optSeqPostOverseer.map(_.toSet) match {
      case Some(set) =>
        log.info("overseers posted")
        log.debug(s"overseers posted: postedOverseers: $set, chatId=$chatId, userId=$userId")
        MDC.remove("repMethod")
        Some(set)

      case None =>
        log.info(CHAT_NOT_FOUND)
        log.debug(s"$CHAT_NOT_FOUND: chatId=$chatId, userId=$userId")
        MDC.remove("repMethod")
        None
    }
  }

  def postOverseers(postOverseers: Set[PostOverseer], chatId: String,
    userId: String): Future[Option[Set[PostOverseer]]] =
    db.run(postOverseersAction(postOverseers, chatId, userId).transactionally)

  private def getOverseersAction(chatId: String, userId: String): DBIO[Option[Set[PostOverseer]]] = {
    for {
      optChatData <- getChatDataAction(chatId, userId)
      result <- optChatData match {
        case Some(_) => getOverseersQuery(chatId, userId).result
          .map(seq => Some(seq.map { case (address, oversightId) => PostOverseer(address, Some(oversightId)) }.toSet))

        case None => DBIO.successful(None)
      }
    } yield result
  }

  def getOverseers(chatId: String, userId: String): Future[Option[Set[PostOverseer]]] =
    db.run(getOverseersAction(chatId, userId).transactionally)

  private def postAttachmentAction(chatId: String, emailId: String, userId: String, filename: String, attachmentPath: String, contentType: Option[String]): DBIO[String] = {
    MDC.put("repMethod", "postAttachmentAction")

    val attachmentId = newUUID
    log.info(logRequest(logPostAttachment))
    log.debug(logRequest(s"$logPostAttachment: chatId=$chatId, emailId=$emailId, userId=$userId, filename=$filename, " +
      s"attachmentId=$attachmentId, attachmentPath=$attachmentPath"))

    (AttachmentsTable.all += AttachmentRow(attachmentId, emailId, filename, attachmentPath, contentType))
      .andThen(DBIO.successful(attachmentId))
  }

  def postAttachment(chatId: String, emailId: String, userId: String, filename: String, attachmentPath: String, contentType: Option[String]): Future[String] =
    db.run(postAttachmentAction(chatId, emailId, userId, filename, attachmentPath, contentType).transactionally)

  def verifyDraftPermissions(chatId: String, emailId: String, userId: String): Future[Boolean] =
    db.run(verifyDraftPermissionsAction(chatId, emailId, userId).transactionally)

  private def getAttachmentsAction(chatId: String, emailId: String, userId: String): DBIO[Option[Set[AttachmentInfo]]] = {
    for {
      verifyIfUserIfAllowed <- verifyIfUserAllowedToSeeEmail(chatId, emailId, userId).result.headOption
      attachmentRows <- AttachmentsTable.all.filter(_.emailId === emailId).result

      attachmentsInfo = attachmentRows
        .map(attachmentRow => AttachmentInfo(attachmentRow.attachmentId, attachmentRow.filename))
        .toSet

    } yield verifyIfUserIfAllowed.map(_ => attachmentsInfo)
  }

  def getAttachments(chatId: String, emailId: String, userId: String): Future[Option[Set[AttachmentInfo]]] = {
    db.run(getAttachmentsAction(chatId, emailId, userId))
  }

  private def getAttachmentAction(chatId: String, emailId: String, attachmentId: String, userId: String): DBIO[Option[AttachmentLocation]] = {
    for {
      verifyIfUserIfAllowed <- verifyIfUserAllowedToSeeEmail(chatId, emailId, userId).result.headOption

      optionAttachmentRow <- AttachmentsTable.all
        .filter(attachmentRow => attachmentRow.emailId === emailId && attachmentRow.attachmentId === attachmentId)
        .result.headOption

    } yield verifyIfUserIfAllowed.flatMap(_ =>
      optionAttachmentRow.map(attachmentRow =>
        AttachmentLocation(attachmentRow.path, attachmentRow.contentType, attachmentRow.filename)))
  }

  def getAttachment(chatId: String, emailId: String, attachmentId: String, userId: String): Future[Option[AttachmentLocation]] =
    db.run(getAttachmentAction(chatId, emailId, attachmentId, userId).transactionally)

  private def deleteAttachmentAction(chatId: String, emailId: String, attachmentId: String, userId: String): DBIO[Boolean] = {
    verifyDraftPermissionsAction(chatId, emailId, userId).flatMap { hasPermission =>
      if (hasPermission) {
        AttachmentsTable.all
          .filter(attachmentRow => attachmentRow.emailId === emailId && attachmentRow.attachmentId === attachmentId)
          .delete
          .map( _ > 0 )
      } else DBIO.successful(false)
    }
  }

  def deleteAttachment(chatId: String, emailId: String, attachmentId: String, userId: String): Future[Boolean] =
    db.run(deleteAttachmentAction(chatId, emailId, attachmentId, userId).transactionally)

  //region Auxiliary Methods

  /**
   * Query that verifies if the user is allowed access to see the email (if is participant or overseer)
   * @param chatId The Id of the given chat
   * @param emailId The Id of the given email
   * @param userId The Id of the given user
   * @return A query with the addressId of the user (with given userId) if the user is allowed to see the email
   */
  private def verifyIfUserAllowedToSeeEmail(chatId: String, emailId: String, userId: String): Query[Rep[String], String, Seq] = {
    for {
      userAddressId <- UsersTable.all.filter(_.userId === userId).map(_.addressId)

      allowedAddressId <- EmailsTable.all.join(EmailAddressesTable.all)
        .on {
          case (emailRow, emailAddressRow) =>
            emailRow.emailId === emailAddressRow.emailId &&
              (emailAddressRow.addressId === userAddressId || emailAddressRow.addressId.in(getUserChatOverseesQuery(userId, chatId))) &&
              (emailRow.sent === 1 || (emailAddressRow.participantType === from && emailAddressRow.addressId === userAddressId))
        }.map(_._2.addressId)
    } yield allowedAddressId
  }

  /**
   * Query that returns a sequence of overseers for a given user within a given chat
   * @param chatId The Id of the given chat
   * @param userId The Id of the given user
   * @return A sequence of pairs, each pair is composed by the address of the overseer and the Id of the oversight
   */
  private def getOverseersQuery(chatId: String, userId: String) =
    for {
      (oversightId, overseerId) <- OversightsTable.all
        .filter(oversightRow => oversightRow.chatId === chatId && oversightRow.overseeId === userId)
        .map(oversightRow => (oversightRow.oversightId, oversightRow.overseerId))

      addressId <- UsersTable.all.filter(_.userId === overseerId).map(_.addressId)

      address <- AddressesTable.all.filter(_.addressId === addressId).map(_.address)

    } yield (address, oversightId)

  private def createNewOverseerAction(overseerId: String, overseerAddress: String, chatId: String,
    userId: String): DBIO[PostOverseer] =
    for {
      optOverseerUserChatId <- UserChatsTable.all.filter(_.userId === overseerId).map(_.userChatId).result.headOption
      _ <- optOverseerUserChatId match {
        case Some(overseerUserChatId) => UserChatsTable.all.filter(_.userChatId === overseerUserChatId)
          .map(_.inbox).update(1)
        case None => UserChatsTable.all += UserChatRow(newUUID, overseerId, chatId, 1, 0, 0, 0)
      }

      oversightId = newUUID

      _ <- OversightsTable.all += OversightRow(oversightId, chatId, overseerId, userId)

    } yield PostOverseer(overseerAddress, Some(oversightId))

  private def postOverseerAction(postOverseer: PostOverseer, chatId: String, userId: String): DBIO[PostOverseer] = {
    for {
      optOverseerId <- getUserIdsByAddressQuery(Set(postOverseer.address)).result.headOption
      optOversightId <- optOverseerId match {
        case Some(overseerId) => OversightsTable.all.filter(
          oversightrow => oversightrow.chatId === chatId &&
            oversightrow.overseerId === overseerId &&
            oversightrow.overseeId === userId).map(_.oversightId).result.headOption
        case None => DBIO.successful(None)
      }

      postedOverseer <- (optOverseerId, optOversightId) match {
        case (_, Some(oversightId)) => DBIO.successful(PostOverseer(postOverseer.address, Some(oversightId)))
        case (None, _) => DBIO.successful(PostOverseer(postOverseer.address, None))
        case (Some(overseerId), None) => createNewOverseerAction(overseerId, postOverseer.address, chatId, userId)
      }
    } yield postedOverseer

  }

  private def deleteOverseerAction(chatId: String, oversightId: String, userId: String): DBIO[Boolean] = {
    MDC.put("repMethod", "deleteOverseerAction")
    log.info(logRequest(logDeleteOverseer))
    log.debug(logRequest(s"$logDeleteOverseer: userId=$userId, chatId=$chatId, oversightId=$oversightId"))

    for {
      optChatData <- getChatDataAction(chatId, userId)
      result <- optChatData match {
        case Some(_) => deleteOversightRow(chatId, oversightId, userId)

        case None => DBIO.successful(false)
      }
    } yield if (result) {
      log.info("overseer deleted")
      log.debug(s"overseer deleted: chatId=$chatId, oversightId=$oversightId, userId=$userId")
      MDC.remove("repMethod")
      true
    } else {
      log.info(CHAT_NOT_FOUND)
      log.debug(s"$CHAT_NOT_FOUND: chatId=$chatId, userId=$userId")
      MDC.remove("repMethod")
      false
    }
  }

  def deleteOverseer(chatId: String, oversightId: String, userId: String): Future[Boolean] =
    db.run(deleteOverseerAction(chatId, oversightId, userId).transactionally)

  private def getOversightsAction(userId: String): DBIO[Option[Oversight]] = {
    MDC.put("repMethod", "getOversightsAction")
    log.info(logRequest(logGetOversights))
    log.debug(logRequest(s"$logGetOversights: userId=$userId"))

    for {
      overseeing <- getOverseeing(DEFAULT_PAGE, DEFAULT_PER_PAGE, DefaultOrder, userId)
        .map { case (chatOverseeings, _, _) => chatOverseeings.headOption }

      overseen <- getOverseen(DEFAULT_PAGE, DEFAULT_PER_PAGE, DefaultOrder, userId)
        .map { case (chatOverseens, _, _) => chatOverseens.headOption }

    } yield {
      (overseeing, overseen) match {
        case (None, None) =>
          log.info(OVERSIGHTS_NOT_FOUND)
          log.debug(s"$OVERSIGHTS_NOT_FOUND: userId=$userId")
          None
        case _ =>
          val oversight = Oversight(overseeing, overseen)
          log.info("Retrieved the preview of the oversights")
          log.debug(s"Retrieved the preview of the oversights: oversight: $oversight, userId=$userId")
          Some(Oversight(overseeing, overseen))
      }
    }
  }

  def getOversights(userId: String): Future[Option[Oversight]] =
    db.run(getOversightsAction(userId).transactionally)

  private[implementations] def getOverseeingsAction(page: Int, perPage: Int, orderBy: OrderBy,
    userId: String): DBIO[Option[(Seq[ChatOverseeing], Int, Int)]] = {
    MDC.put("repMethod", "getOverseeingsAction")
    log.info(logRequest(logGetOverseeings))
    log.debug(logRequest(s"$logGetOverseeings: page=$page, perPage=$perPage, orderBy=$orderBy, userId=$userId"))

    if (page < 0 || perPage <= 0 || perPage > MAX_PER_PAGE) {
      log.info(INVALID_PAGINATION)
      log.debug(s"$INVALID_PAGINATION. page=$page, perPage=$perPage")
      MDC.remove("repMethod")
      DBIO.successful(None)
    } else getOverseeing(page, perPage, orderBy, userId).map {
      case (seqChatOverseeing, totalCount, lastPage) =>
        log.info("Retrieved the paginated overseeings")
        log.debug(s"${
          paginatedResult("seqChatOverseeing", seqChatOverseeing, totalCount, lastPage, page, perPage)
        }, userId=$userId")
        MDC.remove("repMethod")
        Some(seqChatOverseeing, totalCount, lastPage)
    }
  }

  def getOverseeings(page: Int, perPage: Int, orderBy: OrderBy,
    userId: String): Future[Option[(Seq[ChatOverseeing], Int, Int)]] =
    db.run(getOverseeingsAction(page, perPage, orderBy, userId).transactionally)

  private[implementations] def getOverseensAction(page: Int, perPage: Int, orderBy: OrderBy,
    userId: String): DBIO[Option[(Seq[ChatOverseen], Int, Int)]] = {
    MDC.put("repMethod", "getOverseensAction")
    log.info(logRequest(logGetOverseens))
    log.debug(logRequest(s"$logGetOverseens: page=$page, perPage=$perPage, orderBy=$orderBy, userId=$userId"))

    if (page < 0 || perPage <= 0 || perPage > MAX_PER_PAGE) {
      log.info(INVALID_PAGINATION)
      log.debug(s"$INVALID_PAGINATION. page=$page, perPage=$perPage")
      MDC.remove("repMethod")
      DBIO.successful(None)
    } else getOverseen(page, perPage, orderBy, userId).map {
      case (seqChatOverseen, totalCount, lastPage) =>
        log.info("Retrieved the paginated overseens")
        log.debug(s"${
          paginatedResult("seqChatOverseen", seqChatOverseen, totalCount, lastPage, page, perPage)
        }, userId=$userId")
        MDC.remove("repMethod")
        Some(seqChatOverseen, totalCount, lastPage)
    }
  }

  def getOverseens(page: Int, perPage: Int, orderBy: OrderBy,
    userId: String): Future[Option[(Seq[ChatOverseen], Int, Int)]] =
    db.run(getOverseensAction(page, perPage, orderBy, userId).transactionally)

  //region Auxiliary Methods

  /**
   * A query that returns previews of the chats visible to a user possibly filtered to a given Mailbox
   *
   * @param userId The userId of the user in question
   * @param optMailbox The optional Mailbox
   * @return A query that contains a sequence of tuples of (chatId, subject, address, date, body),
   *         The preview of each chat only shows the data of the most recent email
   */
  private def getChatsPreviewQuery(userId: String, optMailbox: Option[Mailbox] = None) = {

    for {
      (chatId, emailId) <- groupedVisibleEmailsQuery(userId, optMailbox)
      subject <- ChatsTable.all.filter(_.chatId === chatId).map(_.subject)
      (emailId, body, date) <- EmailsTable.all.filter(emailRow =>
        emailRow.chatId === chatId && emailRow.emailId === emailId).map(emailRow =>
        (emailRow.emailId, emailRow.body.take(PREVIEW_BODY_LENGTH), emailRow.date))
      addressId <- EmailAddressesTable.all.filter(emailAddressRow =>
        emailAddressRow.emailId === emailId && emailAddressRow.participantType === from)
        .map(_.addressId)
      address <- AddressesTable.all.filter(_.addressId === addressId).map(_.address)

    } yield (chatId, subject, address, date, body)
  }

  private def getOverseeing(page: Int, perPage: Int, orderBy: OrderBy,
    userId: String): DBIO[(Seq[ChatOverseeing], Int, Int)] = {
    val chatsData = (for {
      (chatId, optEmailId) <- groupedVisibleEmailsQuery(userId).filter {
        case (chatId, _) => chatId.in(
          OversightsTable.all.filter(oversightRow => oversightRow.chatId === chatId &&
            oversightRow.overseerId === userId).map(_.chatId))
      }

      (date, body) <- EmailsTable.all.filter(emailRow =>
        emailRow.chatId === chatId && emailRow.emailId === optEmailId)
        .map(emailRow => (emailRow.date, emailRow.body))
    } yield (chatId, date, body)) match {
      case query if orderBy == Asc => query.sortBy { case (_, date, body) => (date.asc, body.asc) }
      case query => query.sortBy { case (_, date, body) => (date.desc, body.asc) }
    }

    for {
      totalCount <- chatsData.length.result
      chatOverseeings <- (for {
        (chatId, date, body) <- chatsData.drop(perPage * page).take(perPage)
        (oversightId, overseeId) <- OversightsTable.all.filter(oversightRow =>
          oversightRow.overseerId === userId && oversightRow.chatId === chatId)
          .map(oversightRow => (oversightRow.oversightId, oversightRow.overseeId))
        overseeAddressId <- UsersTable.all.filter(_.userId === overseeId).map(_.addressId)
        overseeAddress <- AddressesTable.all.filter(_.addressId === overseeAddressId).map(_.address)
      } yield (chatId, date, body, oversightId, overseeAddress)).result
        .map(resultSeq => {
          val seqToOrder = resultSeq.groupBy { case (chatId, date, body, _, _) => (chatId, date, body) }.toSeq
            .map { chatOverseeingData: ((String, String, String), Seq[(String, String, String, String, String)]) =>
              (chatOverseeingData._1._2, chatOverseeingData._1._3,
                ChatOverseeingDatatoDTO(chatOverseeingData._1._1, chatOverseeingData._2))
            }

          val orderedSeq = seqToOrder match {
            case sequence if orderBy == Asc => sequence.sortBy { case (date, body, chatOverseeing) => (date, body) }
            case sequence => sequence.sortBy { case (date, body, chatOverseeing) => (date, body) }(Ordering.Tuple2(
              Ordering.String.reverse,
              Ordering.String))
          }

          orderedSeq.map(_._3)
        })

    } yield (chatOverseeings, totalCount, divide(totalCount, perPage, RoundingMode.CEILING) - 1)
  }

  private def ChatOverseeingDatatoDTO(
    chatId: String,
    dataSeq: Seq[(String, String, String, String, String)]): ChatOverseeing =
    ChatOverseeing(
      chatId,
      dataSeq.map { case (_, _, _, oversightId, overseeAddress) => Overseeing(oversightId, overseeAddress) }.toSet)

  private def getOverseen(page: Int, perPage: Int, orderBy: OrderBy,
    userId: String): DBIO[(Seq[ChatOverseen], Int, Int)] = {
    val chatsData = (for {
      (chatId, optEmailId) <- groupedVisibleEmailsQuery(userId).filter {
        case (chatId, _) => chatId.in(
          OversightsTable.all.filter(oversightRow => oversightRow.chatId === chatId &&
            oversightRow.overseeId === userId).map(_.chatId))
      }

      (date, body) <- EmailsTable.all.filter(emailRow =>
        emailRow.chatId === chatId && emailRow.emailId === optEmailId)
        .map(emailRow => (emailRow.date, emailRow.body))
    } yield (chatId, date, body)) match {
      case query if orderBy == Asc => query.sortBy { case (_, date, body) => (date.asc, body.asc) }
      case query => query.sortBy { case (_, date, body) => (date.desc, body.asc) }
    }

    for {
      totalCount <- chatsData.length.result
      chatOverseens <- (for {
        (chatId, date, body) <- chatsData.drop(perPage * page).take(perPage)
        (oversightId, overseerId) <- OversightsTable.all.filter(oversightRow =>
          oversightRow.overseeId === userId && oversightRow.chatId === chatId)
          .map(oversightRow => (oversightRow.oversightId, oversightRow.overseerId))
        overseerAddressId <- UsersTable.all.filter(_.userId === overseerId).map(_.addressId)
        overseerAddress <- AddressesTable.all.filter(_.addressId === overseerAddressId).map(_.address)
      } yield (chatId, date, body, oversightId, overseerAddress)).result
        .map(resultSeq => {
          val seqToOrder = resultSeq.groupBy { case (chatId, date, body, _, _) => (chatId, date, body) }.toSeq
            .map { chatOverseenData: ((String, String, String), Seq[(String, String, String, String, String)]) =>
              (chatOverseenData._1._2, chatOverseenData._1._3,
                ChatOverseenDatatoDTO(chatOverseenData._1._1, chatOverseenData._2))
            }

          val orderedSeq = seqToOrder match {
            case sequence if orderBy == Asc => sequence.sortBy { case (date, body, chatOverseen) => (date, body) }
            case sequence => sequence.sortBy { case (date, body, chatOverseeing) => (date, body) }(Ordering.Tuple2(
              Ordering.String.reverse,
              Ordering.String))
          }

          orderedSeq.map(_._3)
        })

    } yield (chatOverseens, totalCount, divide(totalCount, perPage, RoundingMode.CEILING) - 1)
  }

  private def ChatOverseenDatatoDTO(
    chatId: String,
    dataSeq: Seq[(String, String, String, String, String)]): ChatOverseen =
    ChatOverseen(
      chatId,
      dataSeq.map { case (_, _, _, oversightId, overseeAddress) => Overseen(oversightId, overseeAddress) }.toSet)

  private def deleteOversightRow(chatId: String, oversightId: String, userId: String): DBIO[Boolean] =
    OversightsTable.all.filter(oversightRow => oversightRow.chatId === chatId &&
      oversightRow.overseeId === userId && oversightRow.oversightId === oversightId).delete
      .map(_ == 1)

  private def getDraftsUserChat(userId: String, chatId: String) =
    UserChatsTable.all
      .filter(userChatRow => userChatRow.userId === userId && userChatRow.chatId === chatId && userChatRow.draft > 0)

  private def getDraftEmailQuery(chatId: String, emailId: String) =
    EmailsTable.all
      .filter(emailRow => emailRow.chatId === chatId && emailRow.emailId === emailId && emailRow.sent === 0)

  private def getEmailAddressesQuery(chatId: String, emailId: String) =
    EmailAddressesTable.all
      .filter(emailAddressRow => emailAddressRow.chatId === chatId && emailAddressRow.emailId === emailId)

  private def getEmailAttachmentsQuery(emailId: String) =
    AttachmentsTable.all.filter(_.emailId === emailId)

  private def deleteChatAndUserChatRows(chatId: String): DBIO[Unit] = {
    DBIO.seq(
      ChatsTable.all.filter(_.chatId === chatId).delete,
      UserChatsTable.all.filter(_.chatId === chatId).delete)
  }

  private def deleteChatIfHasNoEmails(chatId: String, userId: String): DBIO[Unit] = {
    for {
      chatEmails <- EmailsTable.all.filter(_.chatId === chatId).result

      _ <- if (chatEmails.isEmpty) deleteChatAndUserChatRows(chatId)
      else DBIO.successful(Unit)
    } yield ()
  }

  private def deleteDraftRowsAction(chatId: String, emailId: String, userId: String): DBIO[Boolean] = {
    for {
      deleteEmailAddresses <- getEmailAddressesQuery(chatId, emailId).delete

      deleteAttachments <- getEmailAttachmentsQuery(emailId).delete

      deleteEmail <- getDraftEmailQuery(chatId, emailId).delete

      updateUserChat <- UserChatsTable.decrementDrafts(userId, chatId)

      _ <- deleteChatIfHasNoEmails(chatId, userId)

      numberOfDeletedRows = deleteEmailAddresses + deleteAttachments + deleteEmail
    } yield numberOfDeletedRows > 0
  }

  private def verifyDraftPermissionsAction(chatId: String, emailId: String, userId: String): DBIO[Boolean] = {
    for {
      optionUserChat <- getDraftsUserChat(userId, chatId).result.headOption
      optionDraft <- getDraftEmailQuery(chatId, emailId).result.headOption

      fromAddressIdQuery = getEmailAddressesQuery(chatId, emailId).filter(_.participantType === from).map(_.addressId)

      optionFromUserId <- UsersTable.all
        .filter(userRow => userRow.addressId.in(fromAddressIdQuery) && userRow.userId === userId)
        .result.headOption
    } yield List(optionUserChat, optionDraft, optionFromUserId).forall(_.isDefined)
  }

  private def verifyIfChatAlreadyInTrash(chatId: String, userId: String): DBIO[Option[Boolean]] = {
    UserChatsTable.all.filter(userChat => userChat.chatId === chatId && userChat.userId === userId)
      .map(_.trash)
      .result.headOption
      .map(optionUserChat => optionUserChat.map(_ == 1))
  }

  private def changeChatSubjectAction(chatId: String, userId: String, newSubject: String): DBIO[Option[Int]] = {
    for {
      userAddressId <- UsersTable.getUserAddressId(userId).result.headOption

      emailId <- UserChatsTable.all
        .join(EmailsTable.all)
        .on {
          case (userChat, email) =>
            userChat.chatId === email.chatId && email.chatId === chatId && userChat.userId === userId &&
              userChat.draft > 0 && email.sent === 0
        }
        .map { case (userChatssTable, emailsTable) => emailsTable.emailId }.result

      optionAddressId <- EmailAddressesTable.all
        .filter(emailAddress => emailAddress.emailId === emailId.headOption.getOrElse("email not found") &&
          emailAddress.participantType === from &&
          emailAddress.addressId === userAddressId.getOrElse("user not found") && emailId.size == 1)
        .map(_.addressId)
        .result.headOption

      subjectChanged <- DBIO.sequenceOption(optionAddressId.map(_ => ChatsTable.changeSubject(chatId, newSubject)))
    } yield subjectChanged
  }

  private def tryRestoreChatAction(chatId: String, userId: String): DBIO[Option[Int]] = {
    for {
      optionIfChatInTrash <- verifyIfChatAlreadyInTrash(chatId, userId)

      optionNumberOfUpdatedRows <- DBIO.sequenceOption(optionIfChatInTrash.map(chatIsInTrash =>
        if (chatIsInTrash) restoreChatAction(chatId, userId) else DBIO.successful(0)))
    } yield optionNumberOfUpdatedRows
  }

  private def restoreChatAction(chatId: String, userId: String): DBIO[Int] = {
    for {
      participations <- getUserParticipationsOnChatAction(chatId, userId)

      (sender, receiver) = participations.partition { case (participantType, _) => participantType == From }

      chatOversees <- getOverseesUserChat(chatId, userId)

      //Count of the emails where the user is a receiver if and only if the email was already sent
      numberInbox = receiver.count { case (_, sent) => sent == 1 }
      inbox = if (chatOversees.nonEmpty || numberInbox > 0) 1 else 0

      numberSent = sender.map { case (_, sent) => sent }.sum
      numberDrafts = sender.size - numberSent

      sent = if (sender.size - numberDrafts > 0) 1 else 0

      restoreUserChat <- UserChatsTable.restoreChat(userId, chatId, inbox, sent, numberDrafts)

    } yield restoreUserChat
  }

  private def moveToTrashAction(chatId: String, userId: String): DBIO[Option[Int]] = {
    for {
      ifUserChatExists <- UserChatsTable.all.filter(userChat => userChat.chatId === chatId &&
        userChat.userId === userId)
        .result.headOption

      optionNumberOfRowsUpdated <- DBIO.sequenceOption(
        ifUserChatExists.map(_ => UserChatsTable.moveChatToTrash(chatId, userId)))

    } yield optionNumberOfRowsUpdated
  }

  /**
   * Creates a DBIOAction to get all the participations of a user within a given chat
   *
   * @param chatId ID of the chat in question
   * @param userId ID of the user in question
   * @return A DBIOAction that when run returns a sequence of tuples each containing a participantType of the user,
   *         along with the sent status of the email
   *         Seq((participantType, sent))
   */
  private def getUserParticipationsOnChatAction(chatId: String, userId: String): DBIO[Seq[(ParticipantType, Int)]] = {
    EmailAddressesTable.all
      .join(EmailsTable.all)
      .on {
        case (emailAddress, email) =>
          emailAddress.chatId === chatId && emailAddress.emailId === email.emailId &&
            emailAddress.addressId.in(UsersTable.getUserAddressId(userId))
      }
      .map { case (emailAddress, email) => (emailAddress.participantType, email.sent) }
      .result
  }

  private def getOverseesUserChat(chatId: String, userId: String): DBIO[Seq[UserChatRow]] = {
    OversightsTable.all.join(UserChatsTable.all)
      .on {
        case (oversight, userChat) =>
          oversight.chatId === chatId && userChat.chatId === oversight.chatId &&
            oversight.overseerId === userId && oversight.overseeId === userChat.userId
      }
      .map { case (_, userChat) => userChat }
      .result
  }

  /**
   * Method that returns an action containing an instance of the class Email
   *
   * @param userId  ID of the user
   * @param emailId ID of the email
   * @return a DBIOAction containing an instance of the class Email
   */
  private def getEmailDTOAction(userId: String, emailId: String): DBIO[Seq[Email]] = {
    for {
      email <- EmailsTable.all
        .join(UserChatsTable.all)
        .on {
          case (emailRow, userChatRow) => emailRow.emailId === emailId && userChatRow.userId === userId &&
            userChatRow.chatId === emailRow.chatId
        }
        .map { case (emailRow, _) => (emailRow.emailId, emailRow.body, emailRow.date, emailRow.sent) }
        .result

      attachmentIds <- AttachmentsTable.all.filter(_.emailId === emailId).map(_.attachmentId).result

      emailAddresses <- EmailAddressesTable.all
        .join(AddressesTable.all)
        .on {
          case (emailAddress, address) => emailAddress.emailId === emailId &&
            emailAddress.addressId === address.addressId
        }
        .map { case (emailAddress, address) => (emailAddress.emailId, emailAddress.participantType, address.address) }
        .result

      groupedEmailAddresses = emailAddresses
        .groupBy { case (thisEmailId, participantType, _) => (thisEmailId, participantType) }
        .mapValues(_.map { case (email_id, participantType, address) => address })

    } yield buildEmailDto(email, groupedEmailAddresses, Map(emailId -> attachmentIds))
  }

  /**
   * Method that, given a sequence of userIds and a chat, returns a Map with the UserChatRows of each user
   * for that chat (if it exists) with their respective userIds as key
   *
   * @param userIds sequence of userIds
   * @param chatId  ID of the chat
   * @return a DBIOAction that returns a Map with the userIds as key and UserChatRows of each user
   *         for that chat as value
   */
  private def getUserChatsByUserId(userIds: Seq[String], chatId: String): DBIO[Map[String, UserChatRow]] = {
    for {
      userIdUserChatTuple <- UserChatsTable.all
        .filter(userChat => userChat.userId.inSet(userIds) && userChat.chatId === chatId)
        .map(userChat => userChat.userId -> userChat)
        .result
    } yield userIdUserChatTuple.toMap
  }

  /**
   * Method that sends an email by:
   * - inserting or updating the userChat of the receivers (users) to "inbox"
   * - updating the email status to "sent"
   * - updating the userChat of the sender (user) to "sent"
   *
   * @param senderUserId userId of the sender
   * @param chatId       ID of the chat
   * @param emailId      ID of the email
   * @param addresses    addresses of the receivers of the email
   * @return an action containing the count of all the updated rows
   */
  private def sendEmailAction(senderUserId: String, chatId: String, emailId: String, addresses: Set[String]): DBIO[Int] = {
    if (addresses.nonEmpty) {
      (for {
        updateReceiversChats <- updateReceiversUserChatsToInbox(chatId, addresses)
        updateEmailStatus <- EmailsTable.all
          .filter(_.emailId === emailId).map(emailRow => (emailRow.date, emailRow.sent))
          .update(DateUtils.getCurrentDate, 1)
        updateSenderChat <- UserChatsTable.userEmailWasSent(senderUserId, chatId)
      } yield updateReceiversChats.sum + updateEmailStatus + updateSenderChat).transactionally
    } else DBIO.successful(0)
  }

  /**
   * Method that updates the user's chat status to "inbox".
   * Given a list of addresses, it filters the ones that correspond to a user and then retrieves the userChatRows
   * for the users who already have the chat. Then updates that row to "inbox" or
   * inserts a new row for that user and chat
   *
   * @param chatId    ID of the chat
   * @param addresses addresses of the people that are going to receive the email (not all of them are users)
   * @return DBIOAction that performs this update
   */
  private def updateReceiversUserChatsToInbox(chatId: String, addresses: Set[String]) = {
    for {
      receiversUserIds <- getUserIdsByAddressQuery(addresses).result
      receiversWithChat <- getUserChatsByUserId(receiversUserIds, chatId)

      updateReceiversChats <- DBIO.sequence(
        receiversUserIds.map(receiverId =>
          UserChatsTable.all.insertOrUpdate(
            receiversWithChat.getOrElse(receiverId, UserChatRow(newUUID, receiverId, chatId, 1, 0, 0, 0))
              .copy(inbox = 1))))
    } yield updateReceiversChats
  }

  /**
   * Method that, given a list of email addresses, gets the userId of the user linked to that address (if there is one)
   *
   * @param addresses list of addresses
   * @return a list of userIds of those addresses
   */
  private def getUserIdsByAddressQuery(addresses: Set[String]): Query[Rep[String], String, scala.Seq] =
    AddressesTable.all.join(UsersTable.all)
      .on((address, user) => address.address.inSet(addresses) && address.addressId === user.addressId)
      .map { case (addressesTable, usersTable) => usersTable.userId }

  /**
   * Method that, given an emailId, gets all the addresses involved in that email
   * and groups them by participation type (from, to, bcc, cc)
   *
   * @param emailId ID of the email
   * @return a Map with "participantType" as key and the tuple (participantType, addressId, address) as value
   */
  private def getEmailAddressByGroupedByParticipantType(emailId: String): DBIO[Map[ParticipantType, Seq[(ParticipantType, String, String)]]] = {
    for {
      addresses <- EmailAddressesTable.all.join(AddressesTable.all)
        .on((emailAddressRow, addressRow) => emailAddressRow.emailId === emailId &&
          emailAddressRow.addressId === addressRow.addressId)
        .map {
          case (emailAddressRow, addressRow) =>
            (emailAddressRow.participantType, addressRow.addressId, addressRow.address)
        }
        .result
    } yield addresses.groupBy { case (participantType, addressId, address) => participantType }
  }

  /**
   * Method that gets the from address of an email. It also verifies if this from address is the user's address
   * and if the email (with given emailId) is a part of the chat (with given chatId)
   *
   * @param chatId  ID of the chat
   * @param emailId ID of the email
   * @param userId  ID of the user of the address to return
   * @return address of the user with userId that is also the sender (From) of the email
   */
  private def getVerifiedFromAddressQuery(chatId: String, emailId: String, userId: String): Query[Rep[String], String, scala.Seq] = {
    UserChatsTable.all
      .join(EmailsTable.all).on {
        case (userChatRow, emailRow) => userChatRow.chatId === emailRow.chatId &&
          userChatRow.chatId === chatId && userChatRow.userId === userId &&
          userChatRow.draft > 0 && emailRow.emailId === emailId && emailRow.sent === 0 //must be a draft from this user
      }.map { case (userChatsTable, emailsTable) => emailsTable }
      .join(EmailAddressesTable.all).on {
        case (emailRow, emailAddressRow) => emailRow.emailId === emailAddressRow.emailId &&
          emailAddressRow.participantType === from
      }.map { case (emailsTable, emailsAddressesTable) => emailsAddressesTable }
      .join(AddressesTable.all).on {
        case (emailAddressRow, addressRow) => emailAddressRow.addressId === addressRow.addressId
      }.map { case (emailsAddressesTable, addressesTable) => addressesTable }
      .join(UsersTable.all).on {
        case (addressRow, userRow) => userRow.userId === userId &&
          addressRow.addressId === userRow.addressId
      }.map { case (addressRow, userRow) => addressRow.address }
  }

  /**
   * Method that, given the receiver participation type (to, bcc, cc):
   * - Inserts new email addresses in the database if the patch contains new ones
   * - Deletes old email addresses from the database if the patch does not include them
   *
   * @param emailId            ID of the email
   * @param chatId             ID of the chat
   * @param participantType    type of participant (to, bcc or cc)
   * @param optionNewAddresses optional set of new addresses of the referred participantType. If it is None,
   *                           no addresses will be added or deleted
   * @return an optional set of the addresses that stayed in the database after all the additions and deletions
   */
  private def insertAndDeleteAddressesByParticipantTypeAction(emailId: String, chatId: String,
    participantType: ParticipantType, optionNewAddresses: Option[Set[String]]): DBIO[Set[String]] = {

    for {
      groupedExistingAddresses <- getEmailAddressByGroupedByParticipantType(emailId)

      existingAddresses = groupedExistingAddresses.getOrElse(participantType, Seq())
        .map { case (_, addressId, address) => (addressId, address) }
      //The addressId is needed to delete. The address is needed to add.

      //Addresses to delete: addresses that are in the database but are not in the patch
      deleteAddresses <- optionNewAddresses.map(newAddresses =>
        existingAddresses.filterNot { case (_, address) => newAddresses.contains(address) }
          .map { case (addressId, _) => addressId })
        .map(addressesToDelete =>
          EmailAddressesTable.all.filter(emailAddressRow =>
            emailAddressRow.emailId === emailId && emailAddressRow.addressId.inSet(addressesToDelete) &&
              emailAddressRow.participantType === participantType)
            .delete)
        .getOrElse(DBIO.successful(0))

      //Addresses to add: addresses that are in the patch but are not in the database yet
      addAddresses <- DBIO.sequence(optionNewAddresses.map(newAddresses =>
        newAddresses.filterNot(address => existingAddresses.map(_._2).contains(address)).toSeq)
        .map(addressesToAdd =>
          addressesToAdd.map(address =>
            insertEmailAddress(emailId, chatId, upsertAddress(address), participantType)))
        .getOrElse(Seq(DBIO.successful(0))))

    } yield optionNewAddresses match {
      case Some(addedNewAddresses) => addedNewAddresses
      case None => existingAddresses.map { case (_, address) => address }.toSet
    }
  }

  /**
   * Method that, given the three different receiver types (to, bcc, cc) and the update of an email,
   * inserts the new email addresses (that are not in the database) and deletes from the database
   * the ones that are not in the update
   *
   * @param upsertEmail DTO that represents the email data to be updated
   * @param chatId      the ID of the chat
   * @param emailId     the ID of the email
   * @return a DBIOAction that does all the inserts and deletes and retrieves a sequence with the resulting
   *         addresses (the ones that remain in the database after the deletions and insertions)
   */
  private def updateEmailAddresses(upsertEmail: UpsertEmail, chatId: String, emailId: String): DBIO[Set[String]] = {

    for {
      toUpsert <- insertAndDeleteAddressesByParticipantTypeAction(emailId, chatId, To, upsertEmail.to)
      bccUpsert <- insertAndDeleteAddressesByParticipantTypeAction(emailId, chatId, Bcc, upsertEmail.bcc)
      ccUpsert <- insertAndDeleteAddressesByParticipantTypeAction(emailId, chatId, Cc, upsertEmail.cc)

    } yield toUpsert ++ bccUpsert ++ ccUpsert
  }

  /**
   * Method that updates the email row of an email as well as the email's addresses
   *
   * @param upsertEmail DTO that contains the email data
   * @param chatId      ID of the chat
   * @param emailId     ID of the email
   * @return The Action that updates the email row and the emailAddress rows. This action returns an option
   *         of the receiving addresses of the patched email. If the user is not authorized to patch this email,
   *         this option will be None.
   */
  private def updateEmailAction(upsertEmail: UpsertEmail, chatId: String, emailId: String,
    userId: String): DBIO[Option[Set[String]]] = {
    for {
      optionVerifiedFromAddress <- getVerifiedFromAddressQuery(chatId, emailId, userId).result.headOption

      newBody = upsertEmail.body
      updateBody = if (newBody.isDefined) updateEmailBody(newBody.get, chatId, emailId) else DBIO.successful(0)
      updateAddresses = updateEmailAddresses(upsertEmail, chatId, emailId)

      update <- DBIO.sequenceOption(optionVerifiedFromAddress.map(_ =>
        updateBody.andThen(updateAddresses)))

    } yield update
  }

  /**
   * Method that updates the body of an email, as well as the date
   *
   * @param newBody new body of the email to update the database
   * @param chatId  ID of the chat
   * @param emailId ID of the email
   * @return a DBIOAction that returns an Int that represents the number of updated rows
   */
  private def updateEmailBody(newBody: String, chatId: String, emailId: String): DBIO[Int] = {
    EmailsTable.all
      .filter(emailRow => emailRow.emailId === emailId && emailRow.chatId === chatId && emailRow.sent === 0)
      .map(emailRow => (emailRow.body, emailRow.date))
      .update(newBody, DateUtils.getCurrentDate)
  }

  /**
   * Method that inserts a new row for the email in draft state and also inserts new rows for the addresses
   *
   * @param upsertEmail DTO containing the data of the email
   * @param chatId      the ID of the chat
   * @param emailId     the ID of the email
   * @param fromAddress the address of the sender (From)
   * @param date        current date
   * @return the action that inserts a new email and inserts/updates its addresses
   */
  private def insertEmailAndAddresses(upsertEmail: UpsertEmail, chatId: String,
    emailId: String, fromAddress: String, date: String): DBIO[Unit] = {
    for {
      _ <- EmailsTable.all += EmailRow(emailId, chatId, upsertEmail.body.getOrElse(""), date, 0)

      fromInsert = insertEmailAddress(emailId, chatId, upsertAddress(fromAddress), From)
      toInsert = upsertEmail.to.getOrElse(Set()).map(
        to => insertEmailAddress(emailId, chatId, upsertAddress(to), To))
      bccInsert = upsertEmail.bcc.getOrElse(Set()).map(
        bcc => insertEmailAddress(emailId, chatId, upsertAddress(bcc), Bcc))
      ccInsert = upsertEmail.cc.getOrElse(Set()).map(
        cc => insertEmailAddress(emailId, chatId, upsertAddress(cc), Cc))

      _ <- DBIO.sequence(Vector(fromInsert) ++ toInsert ++ bccInsert ++ ccInsert)
    } yield ()
  }

  /**
   * Method that inserts a new address if it does not exist and returns the resulting addressId
   *
   * @param address email address to insert
   * @return a DBIOAction that returns the ID of the new address or of the already existing one
   */
  private[implementations] def upsertAddress(address: String): DBIO[String] = {
    for {
      existing <- AddressesTable.selectByAddress(address).result.headOption

      row = existing
        .getOrElse(AddressRow(newUUID, address))

      _ <- AddressesTable.all.insertOrUpdate(row)
    } yield row.addressId
  }

  /**
   * Method that inserts a new EmailAddressRow with a foreign key for an AddressRow
   *
   * @param emailId         ID of the email
   * @param chatId          ID of the chat
   * @param address         DBIOAction that returns the addressId (foreign key for the AddressesTable)
   * @param participantType type of participant (from, to, bcc or cc)
   * @return a DBIOAction with the number of inserted rows
   */
  private[implementations] def insertEmailAddress(emailId: String, chatId: String, address: DBIO[String],
    participantType: ParticipantType): DBIO[Int] =
    for {
      addressId <- address
      numberOfInsertedRows <- EmailAddressesTable.all += EmailAddressRow(newUUID, emailId, chatId, addressId,
        participantType)
    } yield numberOfInsertedRows

  private[implementations] def getUserChatOverseersAction(overseeUserId: String, chatId: String): DBIO[Seq[String]] = {
    OversightsTable.all
      .filter(oversight => oversight.chatId === chatId && oversight.overseeId === overseeUserId)
      .map(_.overseerId)
      .result
  }

  private def checkIfUserHasAccessAndParticipates(chatId: String, userId: String): DBIO[Boolean] =
    (for {
      (userId, addressId) <- UsersTable.all.filter(_.userId === userId)
        .map(userRow => (userRow.userId, userRow.addressId))
      _ <- AddressesTable.all.filter(_.addressId === addressId)
      chatId <- UserChatsTable.all.filter(userChatRow => userChatRow.chatId === chatId &&
        userChatRow.userId === userId &&
        (userChatRow.inbox === 1 || userChatRow.sent === 1 || userChatRow.draft >= 1 || userChatRow.trash === 1))
        .map(_.chatId)
      _ <- ChatsTable.all.filter(_.chatId === chatId)
      participantType <- EmailAddressesTable.all.filter(_.addressId === addressId).map(_.participantType)
    } yield participantType).exists.result

  //region getChat auxiliary methods

  /**
   * A query that groups the emails visible to a user possibly filtered to a given Mailbox. The grouping is done
   * in a way that leaves only one emailId per chat. This email is the most recent with the lowest Id alphabetically
   *
   * @param userId The userId of the user in question
   * @param optMailbox The optional Mailbox
   * @return A query that contains a sequence of tuples of (chatId, Option(emailId)
   */
  private def groupedVisibleEmailsQuery(userId: String, optMailbox: Option[Mailbox] = None) = {
    val visibleEmailsQuery = getVisibleEmailsQuery(userId, optMailbox)

    visibleEmailsQuery
      .map { case (chatId, emailId, body, date, sent) => (chatId, date) }
      .groupBy(_._1)
      .map {
        case (chatId, chatDateQuery) =>
          (chatId, chatDateQuery.map { case (chat, date) => date }.max)
      }
      .join(visibleEmailsQuery)
      .on {
        case ((groupedChatId, maxDate), (chatId, emailId, _, date, _)) =>
          groupedChatId === chatId && maxDate === date
      }
      .map {
        case ((groupedChatId, maxDate), (chatId, emailId, _, date, _)) =>
          (chatId, emailId)
      }
      .groupBy(_._1)
      .map {
        case (chatId, chatEmailQuery) =>
          (chatId, chatEmailQuery.map { case (chat, emailId) => emailId }.min)
      }
  }

  /**
   * Takes a chat and user, and creates a DBIOAction that gives the chat's id the subject and the user's address
   *
   * @param chatId The chat's id
   * @param userId The user's id
   * @return A DBIOAction that returns the chat's id, it's subject and the user's address
   *         (chatId, subject, address)
   */
  private[implementations] def getChatDataAction(chatId: String, userId: String): DBIO[Option[(String, String, String)]] =
    (for {
      subject <- ChatsTable.all.filter(_.chatId === chatId).map(_.subject)
      _ <- UserChatsTable.all.filter(userChatRow => userChatRow.chatId === chatId && userChatRow.userId === userId &&
        (userChatRow.inbox === 1 || userChatRow.sent === 1 || userChatRow.draft >= 1 || userChatRow.trash === 1))
      addressId <- UsersTable.all.filter(_.userId === userId).map(_.addressId)
      address <- AddressesTable.all.filter(_.addressId === addressId).map(_.address)
    } yield (chatId, subject, address)).result.headOption

  /**
   * Method that retrieves the emails of a specific chat that a user can see:
   * - If the user is a participant of the email (from, to, bcc, cc) (the case of the bcc will be handled afterwards)
   * OR if the user is overseeing another user in the chat (has access to the same emails the oversee has,
   * excluding the oversee's drafts)
   * - AND if email is draft (sent = 0), only the user with the From address can see it
   *
   * @param chatId ID of the requested chat
   * @param userId ID of the user that requested the chat
   * @return for each email, returns a tuple (emailId, body, date, sent)
   */
  private def getEmailsQuery(chatId: String, userId: String) =
    getVisibleEmailsQuery(userId)
      .filter(_._1 === chatId)
      .map { case (_, emailId, body, date, sent) => (emailId, body, date, sent) }

  /**
   * Method that, given the emailIds of the emails that the a user can see, for each participant of an email,
   * returns a tuple with (emailId, participantType, address).
   *
   * Note that a user will not see a 'bcc' participant unless the user (or one of their oversees) is either
   * the 'bcc' in question or the person who sent the email
   *
   * @param userId        ID of the user
   * @param emailIdsQuery Query with the emailIds of the chat to show in the response, already filtered
   *                      by the emails the user has permission to see (including the user's oversees)
   * @return for each participant of an email, returns a tuple with (emailId, participantType, address)
   */
  private def getEmailAddressesQuery(userId: String, emailIdsQuery: Query[Rep[String], String, scala.Seq]) = {
    val userAddressIdQuery = UsersTable.all.filter(_.userId === userId).map(_.addressId)

    for {
      userAddressId <- userAddressIdQuery
      emailId <- emailIdsQuery
      //from address of this email
      fromAddressId <- EmailAddressesTable.all
        .filter(ea => ea.emailId === emailId && ea.participantType === from)
        .map(_.addressId)

      (participantType, addressId, address) <- EmailAddressesTable.all.join(AddressesTable.all)
        .on((emailAddressRow, addressRow) => {
          val userOverseesOfThisChat = getUserChatOverseesQuery(userId, emailAddressRow.chatId)
          emailAddressRow.emailId === emailId &&
            emailAddressRow.addressId === addressRow.addressId &&
            (emailAddressRow.participantType =!= bcc ||
              (
                emailAddressRow.addressId === userAddressId ||
                fromAddressId === userAddressId ||
                emailAddressRow.addressId.in(userOverseesOfThisChat) ||
                fromAddressId.in(userOverseesOfThisChat)))
        })
        .map {
          case (emailAddressRow, addressRow) =>
            (emailAddressRow.participantType, emailAddressRow.addressId, addressRow.address)
        }

    } yield (emailId, participantType, address)
  }

  /**
   * Creates a DBIOAction that retrieves all the email addresses (that the user is allowed to see)
   * involved in the chat requested, and the paginated sequence of all the emails of the chat that the user can see
   *
   * @param chatId ID of the requested chat
   * @param page The page being seen
   * @param perPage The number of emails per page
   * @param userId ID of the user requesting the chat
   * @param getAll Boolean used to indicate if all emails should be returned. False by default
   * @return A DBIOAction that returns the tuple (chatEmailAddresses, sequenceOfEmailDTOs, totalCount)
   */
  private def getGroupedEmailsAndAddresses(chatId: String, page: Int, perPage: Int, userId: String, orderBy: OrderBy,
    getAll: Boolean): DBIO[(Set[String], Seq[Email], Int)] = {
    // Paginated query to get all the emails of this chat that the user can see
    val orderedEmailsQuery =
      if (orderBy == Desc)
        getEmailsQuery(chatId, userId).sortBy { case (_, body, date, _) => (date.desc, body.asc) }
      else getEmailsQuery(chatId, userId).sortBy { case (_, body, date, _) => (date.asc, body.asc) }

    // Query to get all the addresses involved in the emails of this chat
    // emailId, participationType, address)(
    val emailAddressesQuery =
      getEmailAddressesQuery(userId, orderedEmailsQuery.map { case (emailId, _, _, _) => emailId })

    for {
      totalCount <- orderedEmailsQuery.length.result
      emails <- if (getAll) orderedEmailsQuery.result
      else orderedEmailsQuery.drop(perPage * page).take(perPage).result
      emailAddressesResult <- emailAddressesQuery.result

      groupedEmailAddresses = emailAddressesResult
        .groupBy { case (emailId, participationType, address) => (emailId, participationType) }
        //group by email ID and receiver type (from, to, bcc, cc)
        .mapValues(_.map { case (emailId, participationType, address) => address })
      // Map: (emailId, receiverType) -> addresses

      // All addresses that sent and received emails in this chat
      chatAddressesResult = emailAddressesResult.map { case (_, _, address) => address }.distinct

      attachments <- getEmailsAttachments(orderedEmailsQuery.map { case (emailId, _, _, _) => emailId })

    } yield (chatAddressesResult.toSet, buildEmailDto(emails, groupedEmailAddresses, attachments), totalCount)
  }

  /**
   * Creates a DBIOAction that retrieves all the IDs of the attachments of each email of the chat
   *
   * @param emailsIds query with the IDs of the emails the user is allowed to see
   * @return A DBIOAction with a Map with the attachment IDs grouped by email ID
   */
  private def getEmailsAttachments(emailsIds: Query[Rep[String], String, scala.Seq]): DBIO[Map[String, Seq[String]]] =
    AttachmentsTable.all
      .filter(_.emailId in emailsIds)
      .map(attachment => (attachment.emailId, attachment.attachmentId)).result
      .map(_
        .groupBy { case (emailId, attachmentId) => emailId }
        .mapValues(_.map { case (emailId, attachmentId) => attachmentId }))

  /**
   * Method that links and merges the emails with its addresses and attachments
   *
   * @param emails      Sequence of email tuples with (emailId, body, date, sent)
   * @param addresses   Map of the email addresses grouped by email ID and participant Type
   * @param attachments Map of the attachment IDs grouped by email ID
   * @return a Sequence of Email(emailId, from, to, bcc, cc, body, date, sent, attachments) DTOs
   */
  private def buildEmailDto(
    emails: Seq[(String, String, String, Int)],
    addresses: Map[(String, ParticipantType), Seq[String]],
    attachments: Map[String, Seq[String]]): Seq[Email] = {

    emails.map {
      case (emailId, body, date, sent) =>
        Email(
          emailId,
          addresses.getOrElse((emailId, From), Seq()).headOption.getOrElse(""),
          addresses.getOrElse((emailId, To), Seq()).toSet,
          addresses.getOrElse((emailId, Bcc), Seq()).toSet,
          addresses.getOrElse((emailId, Cc), Seq()).toSet,
          body,
          date,
          sent,
          attachments.getOrElse(emailId, Seq()).toSet)
    }
  }

  /**
   * Creates a DBIOAction that retrieves all the overseers of a specific chat
   * grouped by the user who gave the oversight permission
   *
   * @param chatId ID of the requested chat
   * @return A DBIOAction that returns a Sequence of Overseer(userAddress, overseersAddresses) DTOs
   */
  private def getOverseersData(chatId: String): DBIO[Set[Overseers]] = {
    val chatOverseersQuery = for {
      (overseerId, overseeId) <- OversightsTable.all.filter(_.chatId === chatId)
        .map(row => (row.overseerId, row.overseeId))

      overseeAddressId <- UsersTable.all.filter(_.userId === overseeId).map(_.addressId)
      overseeAddress <- AddressesTable.all.filter(_.addressId === overseeAddressId)
        .map(_.address)

      overseerAddressId <- UsersTable.all.filter(_.userId === overseerId).map(_.addressId)
      overseerAddress <- AddressesTable.all.filter(_.addressId === overseerAddressId)
        .map(_.address)
    } yield (overseeAddress, overseerAddress)

    chatOverseersQuery.result
      .map(_
        .groupBy { case (overseeAddress, overseerAddress) => overseeAddress }
        .mapValues(_.map { case (overseeAddress, overseerAddress) => overseerAddress }.toSet)
        .toSeq
        .map(Overseers.tupled)
        .toSet)
  }

  //endregion
  //endregion

}