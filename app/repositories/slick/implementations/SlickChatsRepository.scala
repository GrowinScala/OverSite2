package repositories.slick.implementations

import javax.inject.Inject
import model.dtos.PatchChatDTO._
import model.dtos._
import model.types.Mailbox
import model.types.Mailbox._
import repositories.ChatsRepository
import repositories.slick.mappings._
import repositories.dtos._
import slick.dbio.DBIOAction
import slick.jdbc.MySQLProfile.api._
import utils.DateUtils
import utils.Generators._

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }

class SlickChatsRepository @Inject() (db: Database)(implicit executionContext: ExecutionContext)
  extends ChatsRepository {

  val PREVIEW_BODY_LENGTH: Int = 30

  //region Shared auxiliary methods
  /**
   * This query returns, for a user, all of its chats and for EACH chat, all of its emails
   * (without body) and for each email, all of its participants
   * @param userId Id of the user
   * @param optBox Optional Mailbox specification. If used, a chat will only be shown if the user has it inside
   *               the specified mailbox.
   * @return Query: For a user, all of its chats and for EACH chat, all of its emails
   * (without body) and for each email, all of its participants
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
   * @param userId The user in question
   * @param optBox Optional filter for a given mailbox
   * @return Query for the emails that a specific user can see
   *         in a tuple containing (chatId, emailId, body, date, sent):
   * - If the user is a participant of the email (from, to, bcc, cc)
   *   OR if the user is overseeing another user in the chat (has access to the same emails the oversee has,
   *   excluding the oversee's drafts)
   * - AND if email is draft (sent = 0), only the user with the "from" address can see it
   */
  private def getVisibleEmailsQuery(userId: String, optBox: Option[Mailbox] = None) =
    (for {
      userAddressId <- UsersTable.all.filter(_.userId === userId).map(_.addressId)

      (chatId, emailId, body, date, sent) <- getChatsMetadataQueryByUserId(userId, optBox).filter {
        case (chatId, emailId, body, date, sent, addressId, participantType) =>
          (addressId === userAddressId || addressId.in(getUserChatOverseesQuery(userId, chatId))) &&
            (sent === 1 || (participantType === "from" && addressId === userAddressId))
      }.map(filteredRow => (filteredRow._1, filteredRow._2, filteredRow._3, filteredRow._4, filteredRow._5))

    } yield (chatId, emailId, body, date, sent)).distinct
  //endregion

  /**
   * Creates a DBIOAction that returns a preview of all the chats of a given user in a given Mailbox
   * @param mailbox The mailbox being seen
   * @param userId The userId of the user in question
   * @return A DBIOAction that when run returns a sequence of ChatPreview dtos.
   *         The preview of each chat only shows the most recent email
   */
  private[implementations] def getChatsPreviewAction(mailbox: Mailbox, userId: String) = {

    val visibleEmailsQuery = getVisibleEmailsQuery(userId, Some(mailbox))

    val groupedQuery = visibleEmailsQuery
      .map { case (chatId, emailId, body, date, sent) => (chatId, date) }
      .groupBy(_._1)
      .map { case (chatId, date) => (chatId, date.map(_._2).max) }
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

    val chatPreviewQuery = for {
      (chatId, emailId) <- groupedQuery
      subject <- ChatsTable.all.filter(_.chatId === chatId).map(_.subject)
      (emailId, body, date) <- EmailsTable.all.filter(emailRow =>
        emailRow.chatId === chatId && emailRow.emailId === emailId).map(emailRow =>
        (emailRow.emailId, emailRow.body.take(PREVIEW_BODY_LENGTH), emailRow.date))
      addressId <- EmailAddressesTable.all.filter(emailAddressRow =>
        emailAddressRow.emailId === emailId && emailAddressRow.participantType === "from")
        .map(_.addressId)
      address <- AddressesTable.all.filter(_.addressId === addressId).map(_.address)

    } yield (chatId, subject, address, date, body)

    chatPreviewQuery
      .sortBy { case (chatId, subject, address, date, body) => (date.desc, body.asc, address.asc) }
      .result
      .map(_.map(ChatPreview.tupled))
  }

  /**
   * Method that returns a preview of all the chats of a given user in a given Mailbox
   * @param mailbox The mailbox being seen
   * @param userId The userId of the user in question
   * @return A Future sequence of ChatPreview dtos. The preview of each chat only shows the most recent email
   */
  def getChatsPreview(mailbox: Mailbox, userId: String): Future[Seq[ChatPreview]] =
    db.run(getChatsPreviewAction(mailbox, userId).transactionally)

  /**
   * Creates a DBIOAction to get the emails and other data of a specific chat of a user
   * @param chatId ID of the chat requested
   * @param userId ID of the user who requested the chat
   * @return A DBIOAction that when run returns a Chat DTO that carries
   *         the chat's subject,
   *         the addresses involved in the chat,
   *         the overseers of the chat
   *         and the emails of the chat (that the user can see)
   */
  private[implementations] def getChatAction(chatId: String, userId: String): DBIO[Option[Chat]] = {

    for {
      chatData <- getChatDataAction(chatId, userId)
      (addresses, emails) <- getGroupedEmailsAndAddresses(chatId, userId)
      overseers <- getOverseersData(chatId)
    } yield chatData.map {
      case (id, subject, _) =>
        Chat(
          id,
          subject,
          addresses,
          overseers,
          emails)
    }

  }

  /**
   * Method to get the emails and other data of a specific chat of a user
   * @param chatId ID of the chat requested
   * @param userId ID of the user who requested the chat
   * @return a Chat DTO that carries
   *         the chat's subject,
   *         the addresses involved in the chat,
   *         the overseers of the chat
   *         and the emails of the chat (that the user can see)
   */
  def getChat(chatId: String, userId: String): Future[Option[Chat]] =
    db.run(getChatAction(chatId, userId).transactionally)

  /**
   * Creates a DBIOAction that inserts a chat with an email into the database
   * @param createChatDTO The DTO that contains the Chat
   * @param userId The Id of the User who is inserting the chat
   * @return A DBIO that returns a copy of the original createChatDTO but with the Ids of the created chat and email
   *         as well as the emails date.
   */
  private[implementations] def postChatAction(createChatDTO: CreateChatDTO, userId: String): DBIO[CreateChatDTO] = {
    val emailDTO = createChatDTO.email
    val date = DateUtils.getCurrentDate

    /** Generate chatId, userChatId and emailId **/
    val chatId = newUUID
    val userChatId = newUUID
    val emailId = newUUID

    val inserts = for {
      _ <- ChatsTable.all += ChatRow(chatId, createChatDTO.subject.getOrElse(""))
      _ <- UserChatsTable.all += UserChatRow(userChatId, userId, chatId, 0, 0, 1, 0)

      // This assumes that the authentication guarantees that the user exists and has a correct address
      fromAddress <- UsersTable.all.join(AddressesTable.all)
        .on { case (user, address) => user.addressId === address.addressId && user.userId === userId }
        .map(_._2.address).result.head

      _ <- insertEmailAndAddresses(emailDTO, chatId, emailId, fromAddress, date)

    } yield fromAddress

    inserts.map(fromAddress =>
      createChatDTO.copy(chatId = Some(chatId), email = emailDTO.copy(
        emailId = Some(emailId),
        from = Some(fromAddress), date = Some(date))))
  }

  /**
   * Inserts a chat with an email into the database
   * @param createChatDTO The DTO that contains the Chat
   * @param userId The Id of the User who is inserting the chat
   * @return A Future that contains a copy of the original createChatDTO but with the Ids
   *         of the created chat and email as well as the emails date.
   */
  def postChat(createChatDTO: CreateChatDTO, userId: String): Future[CreateChatDTO] =
    db.run(postChatAction(createChatDTO, userId).transactionally)

  private[implementations] def postEmailAction(upsertEmailDTO: UpsertEmailDTO, chatId: String, userId: String) = {
    val date = DateUtils.getCurrentDate

    val emailId = newUUID

    val insertAndUpdate = for {
      chatAddress <- getChatDataAction(chatId, userId)

      _ <- chatAddress match {
        case Some((chatID, subject, fromAddress)) => insertEmailAndAddresses(upsertEmailDTO, chatId,
          emailId, fromAddress, date)
          .andThen(UserChatsTable.incrementDrafts(userId, chatId))
        case None => DBIOAction.successful(None)
      }

    } yield chatAddress

    insertAndUpdate.map {
      case Some(tuple) => tuple match {
        case (chatID, subject, fromAddress) => Some(CreateChatDTO(
          Some(chatID),
          Some(subject),
          UpsertEmailDTO(
            emailId = Some(emailId),
            from = Some(fromAddress),
            to = upsertEmailDTO.to,
            bcc = upsertEmailDTO.bcc,
            cc = upsertEmailDTO.cc,
            body = upsertEmailDTO.body,
            date = Some(date),
            sent = Some(false))))
      }
      case None => None
    }
  }

  def postEmail(upsertEmailDTO: UpsertEmailDTO, chatId: String, userId: String): Future[Option[CreateChatDTO]] =
    db.run(postEmailAction(upsertEmailDTO, chatId, userId).transactionally)

  private[implementations] def patchEmailAction(upsertEmailDTO: UpsertEmailDTO, chatId: String,
    emailId: String, userId: String): DBIO[Option[Email]] = {
    val updateAndSendEmail = for {
      updatedReceiversAddresses <- updateEmailAction(upsertEmailDTO, chatId, emailId, userId)

      sendEmail <- DBIO.sequenceOption(
        updatedReceiversAddresses.map(receiversAddresses =>
          if (upsertEmailDTO.sent.getOrElse(false))
            sendEmailAction(userId, chatId, emailId, receiversAddresses)
          else DBIO.successful(0)))

    } yield sendEmail

    for {
      optionPatch <- updateAndSendEmail.transactionally
      email <- getEmailDTOAction(userId, emailId)
    } yield optionPatch.flatMap(_ => email.headOption)
  }

  def patchEmail(upsertEmailDTO: UpsertEmailDTO, chatId: String, emailId: String, userId: String): Future[Option[Email]] =
    db.run(patchEmailAction(upsertEmailDTO, chatId, emailId, userId).transactionally)

  private[implementations] def patchChatAction(patchChatDTO: PatchChatDTO, chatId: String, userId: String): DBIO[Option[PatchChatDTO]] =
    for {
      optionPatch <- patchChatDTO match {
        case MoveToTrash => moveToTrashAction(chatId, userId)
        case Restore => tryRestoreChatAction(chatId, userId)
        case ChangeSubject(subject) => changeChatSubjectAction(chatId, userId, subject)
      }
    } yield optionPatch.map(_ => patchChatDTO)

  def patchChat(patchChatDTO: PatchChatDTO, chatId: String, userId: String): Future[Option[PatchChatDTO]] =
    db.run(patchChatAction(patchChatDTO, chatId, userId).transactionally)

  private[implementations] def getEmailAction(chatId: String, emailId: String, userId: String) = {
    getChatAction(chatId, userId)
      .map(optionChat =>
        optionChat
          .map(chat => chat.copy(emails = chat.emails.filter(email => email.emailId == emailId)))
          .filter(_.emails.nonEmpty))
  }

  def getEmail(chatId: String, emailId: String, userId: String): Future[Option[Chat]] = {
    db.run(getEmailAction(chatId, emailId, userId).transactionally)
  }

  private[implementations] def deleteChatAction(chatId: String, userId: String): DBIO[Boolean] = {
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
    } yield updateUserChatRow > 0
  }

  def deleteChat(chatId: String, userId: String): Future[Boolean] =
    db.run(deleteChatAction(chatId, userId).transactionally)

  private[implementations] def deleteDraftAction(chatId: String, emailId: String, userId: String): DBIO[Boolean] = {
    for {
      allowedToDeleteDraft <- verifyConditionsToDeleteDraftAction(chatId, emailId, userId)

      deleted <- if (allowedToDeleteDraft) deleteDraftRowsAction(chatId, emailId, userId).transactionally
      else DBIO.successful(false)
    } yield deleted
  }

  def deleteDraft(chatId: String, emailId: String, userId: String): Future[Boolean] = {
    db.run(deleteDraftAction(chatId, emailId, userId).transactionally)
  }

  private def postOverseersAction(postOverseers: Set[PostOverseer], chatId: String, userId: String): DBIO[Option[Set[PostOverseer]]] =
    for {
      chatAccessAndParticipation <- checkIfUserHasAccessAndParticipates(chatId, userId)

      optSeqPostOverseer <- if (chatAccessAndParticipation)
        DBIO.sequence(postOverseers.map(postOverseerAction(_, chatId, userId)).toSeq)
          .map(Some(_))
      else DBIO.successful(None)

    } yield optSeqPostOverseer.map(_.toSet)

  def postOverseers(postOverseers: Set[PostOverseer], chatId: String, userId: String): Future[Option[Set[PostOverseer]]] =
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

  //region Auxiliary Methods

  private def getOverseersQuery(chatId: String, userId: String) =
    for {
      (oversightId, overseerId) <- OversightsTable.all
        .filter(oversightRow => oversightRow.chatId === chatId && oversightRow.overseeId === userId)
        .map(oversightRow => (oversightRow.oversightId, oversightRow.overseerId))

      addressId <- UsersTable.all.filter(_.userId === overseerId).map(_.addressId)

      address <- AddressesTable.all.filter(_.addressId === addressId).map(_.address)

    } yield (address, oversightId)

  private def createNewOverseerAction(overseerId: String, overseerAddress: String, chatId: String, userId: String): DBIO[PostOverseer] =
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

  private def verifyConditionsToDeleteDraftAction(chatId: String, emailId: String, userId: String) = {
    for {
      optionUserChat <- getDraftsUserChat(userId, chatId).result.headOption
      optionDraft <- getDraftEmailQuery(chatId, emailId).result.headOption

      fromAddressIdQuery = getEmailAddressesQuery(chatId, emailId).filter(_.participantType === "from").map(_.addressId)

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
        .map(_._2.emailId).result

      optionAddressId <- EmailAddressesTable.all
        .filter(emailAddress => emailAddress.emailId === emailId.headOption.getOrElse("email not found") &&
          emailAddress.participantType === "from" &&
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

      (sender, receiver) = participations.partition { case (participantType, _) => participantType == "from" }

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
      ifUserChatExists <- UserChatsTable.all.filter(userChat => userChat.chatId === chatId && userChat.userId === userId)
        .result.headOption

      optionNumberOfRowsUpdated <- DBIO.sequenceOption(
        ifUserChatExists.map(_ => UserChatsTable.moveChatToTrash(chatId, userId)))

    } yield optionNumberOfRowsUpdated
  }

  /**
   * Creates a DBIOAction to get all the participations of a user within a given chat
   * @param chatId ID of the chat in question
   * @param userId ID of the user in question
   * @return A DBIOAction that when run returns a sequence of tuples each containing a participantType of the user,
   *         along with the sent status of the email
   *         Seq((participantType, sent))
   */
  private def getUserParticipationsOnChatAction(chatId: String, userId: String): DBIO[Seq[(String, Int)]] = {
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
   * @param userId ID of the user
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
        .mapValues(_.map(_._3))

    } yield buildEmailDto(email, groupedEmailAddresses, Map(emailId -> attachmentIds))
  }

  /**
   * Method that, given a sequence of userIds and a chat, returns a Map with the UserChatRows of each user
   * for that chat (if it exists) with their respective userIds as key
   * @param userIds sequence of userIds
   * @param chatId ID of the chat
   * @return a DBIOAction that returns a Map with the userIds as key and UserChatRows of each user
   * for that chat as value
   */
  private def getUserChatsByUserId(userIds: Seq[String], chatId: String): DBIOAction[Map[String, UserChatRow], NoStream, Effect.Read] = {
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
   * @param senderUserId userId of the sender
   * @param chatId ID of the chat
   * @param emailId ID of the email
   * @param addresses addresses of the receivers of the email
   * @return an action containing the count of all the updated rows
   */
  private def sendEmailAction(senderUserId: String, chatId: String, emailId: String, addresses: Set[String]): DBIO[Int] = {
    if (addresses.nonEmpty) {
      (for {
        updateReceiversChats <- updateReceiversUserChatsToInbox(chatId, addresses)
        updateEmailStatus <- EmailsTable.all
          .filter(_.emailId === emailId).map(emailRow => (emailRow.date, emailRow.sent)).update(DateUtils.getCurrentDate, 1)
        updateSenderChat <- UserChatsTable.userEmailWasSent(senderUserId, chatId)
      } yield updateReceiversChats.sum + updateEmailStatus + updateSenderChat).transactionally
    } else DBIO.successful(0)
  }

  /**
   * Method that updates the user's chat status to "inbox".
   * Given a list of addresses, it filters the ones that correspond to a user and then retrieves the userChatRows
   * for the users who already have the chat. Then updates that row to "inbox" or inserts a new row for that user and chat
   * @param chatId ID of the chat
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
   * @param addresses list of addresses
   * @return a list of userIds of those addresses
   */
  private def getUserIdsByAddressQuery(addresses: Set[String]): Query[Rep[String], String, scala.Seq] =
    AddressesTable.all.join(UsersTable.all)
      .on((address, user) => address.address.inSet(addresses) && address.addressId === user.addressId)
      .map(_._2.userId)

  /**
   * Method that, given an emailId, gets all the addresses involved in that email
   * and groups them by participation type (from, to, bcc, cc)
   * @param emailId ID of the email
   * @return a Map with "participantType" as key and the tuple (participantType, addressId, address) as value
   */
  private def getEmailAddressByGroupedByParticipantType(emailId: String): DBIOAction[Map[String, Seq[(String, String, String)]], NoStream, Effect.Read] = {
    for {
      addresses <- EmailAddressesTable.all.join(AddressesTable.all)
        .on((emailAddressRow, addressRow) => emailAddressRow.emailId === emailId && emailAddressRow.addressId === addressRow.addressId)
        .map {
          case (emailAddressRow, addressRow) =>
            (emailAddressRow.participantType, addressRow.addressId, addressRow.address)
        }
        .result
    } yield addresses.groupBy(_._1) //groupBy participantType
  }

  /**
   * Method that gets the from address of an email. It also verifies if this from address is the user's address
   * and if the email (with given emailId) is a part of the chat (with given chatId)
   * @param chatId ID of the chat
   * @param emailId ID of the email
   * @param userId ID of the user of the address to return
   * @return address of the user with userId that is also the sender ("from") of the email
   */
  private def getVerifiedFromAddressQuery(chatId: String, emailId: String, userId: String): Query[Rep[String], String, scala.Seq] = {
    UserChatsTable.all
      .join(EmailsTable.all).on {
        case (userChatRow, emailRow) => userChatRow.chatId === emailRow.chatId &&
          userChatRow.chatId === chatId && userChatRow.userId === userId &&
          userChatRow.draft > 0 && emailRow.emailId === emailId && emailRow.sent === 0 //must be a draft from this user
      }.map(_._2)
      .join(EmailAddressesTable.all).on {
        case (emailRow, emailAddressRow) => emailRow.emailId === emailAddressRow.emailId &&
          emailAddressRow.participantType === "from"
      }.map(_._2)
      .join(AddressesTable.all).on {
        case (emailAddressRow, addressRow) => emailAddressRow.addressId === addressRow.addressId
      }.map(_._2)
      .join(UsersTable.all).on {
        case (addressRow, userRow) => userRow.userId === userId &&
          addressRow.addressId === userRow.addressId
      }.map { case (addressRow, userRow) => addressRow.address }
  }

  /**
   * Method that, given the receiver participation type (to, bcc, cc):
   * - Inserts new email addresses in the database if the patch contains new ones
   * - Deletes old email addresses from the database if the patch does not include them
   * @param emailId ID of the email
   * @param chatId ID of the chat
   * @param participantType type of participant (to, bcc or cc)
   * @param optionNewAddresses optional set of new addresses of the referred participantType. If it is None,
   *                           no addresses will be added or deleted
   * @return an optional set of the addresses that stayed in the database after all the additions and deletions
   */
  private def insertAndDeleteAddressesByParticipantTypeAction(emailId: String, chatId: String,
    participantType: String, optionNewAddresses: Option[Set[String]]): DBIO[Set[String]] = {

    for {
      groupedExistingAddresses <- getEmailAddressByGroupedByParticipantType(emailId)

      existingAddresses = groupedExistingAddresses.getOrElse(participantType, Seq())
        .map { case (_, addressId, address) => (addressId, address) }
      //The addressId is needed to delete. The address is needed to add.

      //Addresses to delete: addresses that are in the database but are not in the patch
      deleteAddresses <- optionNewAddresses.map(newAddresses =>
        existingAddresses.filterNot { case (_, address) => newAddresses.contains(address) }.map(_._1))
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
      case None => existingAddresses.map(_._2).toSet
    }
  }

  /**
   * Method that, given the three different receiver types (to, bcc, cc) and the update of an email,
   * inserts the new email addresses (that are not in the database) and deletes from the database
   * the ones that are not in the update
   * @param upsertEmailDTO DTO that represents the email data to be updated
   * @param chatId the ID of the chat
   * @param emailId the ID of the email
   * @return a DBIOAction that does all the inserts and deletes and retrieves a sequence with the resulting
   *         addresses (the ones that remain in the database after the deletions and insertions)
   */
  private def updateEmailAddresses(upsertEmailDTO: UpsertEmailDTO, chatId: String, emailId: String) = {

    for {
      toUpsert <- insertAndDeleteAddressesByParticipantTypeAction(emailId, chatId, "to", upsertEmailDTO.to)
      bccUpsert <- insertAndDeleteAddressesByParticipantTypeAction(emailId, chatId, "bcc", upsertEmailDTO.bcc)
      ccUpsert <- insertAndDeleteAddressesByParticipantTypeAction(emailId, chatId, "cc", upsertEmailDTO.cc)

    } yield toUpsert ++ bccUpsert ++ ccUpsert
  }

  /**
   * Method that updates the email row of an email as well as the email's addresses
   * @param upsertEmailDTO DTO that contains the email data
   * @param chatId ID of the chat
   * @param emailId ID of the email
   * @return the action that updates the email row and the emailAddress rows
   */
  private def updateEmailAction(upsertEmailDTO: UpsertEmailDTO, chatId: String, emailId: String, userId: String): DBIO[Option[Set[String]]] = {
    for {
      optionVerifiedFromAddress <- getVerifiedFromAddressQuery(chatId, emailId, userId).result.headOption

      newBody = upsertEmailDTO.body
      updateBody = if (newBody.isDefined) updateEmailBody(newBody.get, chatId, emailId) else DBIO.successful(0)
      updateAddresses = updateEmailAddresses(upsertEmailDTO, chatId, emailId)

      update <- DBIO.sequenceOption(optionVerifiedFromAddress.map(_ =>
        updateBody.andThen(updateAddresses)))

    } yield update
  }

  /**
   * Method that updates the body of an email, as well as the date
   * @param newBody new body of the email to update the database
   * @param chatId ID of the chat
   * @param emailId ID of the email
   * @return a DBIOAction that returns an Int that represents the number of updated rows
   */
  private def updateEmailBody(newBody: String, chatId: String, emailId: String) = {
    EmailsTable.all
      .filter(emailRow => emailRow.emailId === emailId && emailRow.chatId === chatId && emailRow.sent === 0)
      .map(emailRow => (emailRow.body, emailRow.date))
      .update(newBody, DateUtils.getCurrentDate)
  }

  /**
   * Method that inserts a new row for the email in draft state and also inserts new rows for the addresses
   * @param upsertEmailDTO DTO containing the data of the email
   * @param chatId the ID of the chat
   * @param emailId the ID of the email
   * @param fromAddress the address of the sender ("from")
   * @param date current date
   * @return the action that inserts a new email and inserts/updates its addresses
   */
  private def insertEmailAndAddresses(upsertEmailDTO: UpsertEmailDTO, chatId: String,
    emailId: String, fromAddress: String, date: String) = {
    for {
      _ <- EmailsTable.all += EmailRow(emailId, chatId, upsertEmailDTO.body.getOrElse(""), date, 0)

      fromInsert = insertEmailAddress(emailId, chatId, upsertAddress(fromAddress), "from")
      toInsert = upsertEmailDTO.to.getOrElse(Set()).map(
        to => insertEmailAddress(emailId, chatId, upsertAddress(to), "to"))
      bccInsert = upsertEmailDTO.bcc.getOrElse(Set()).map(
        bcc => insertEmailAddress(emailId, chatId, upsertAddress(bcc), "bcc"))
      ccInsert = upsertEmailDTO.cc.getOrElse(Set()).map(
        cc => insertEmailAddress(emailId, chatId, upsertAddress(cc), "cc"))

      _ <- DBIO.sequence(Vector(fromInsert) ++ toInsert ++ bccInsert ++ ccInsert)
    } yield ()
  }

  /**
   * Method that inserts a new address if it does not exist and returns the resulting addressId
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
   * @param emailId ID of the email
   * @param chatId ID of the chat
   * @param address  DBIOAction that returns the addressId (foreign key for the AddressesTable)
   * @param participantType type of participant (from, to, bcc or cc)
   * @return a DBIOAction with the number of inserted rows
   */
  private[implementations] def insertEmailAddress(emailId: String, chatId: String, address: DBIO[String], participantType: String) =
    for {
      addressId <- address
      numberOfInsertedRows <- EmailAddressesTable.all += EmailAddressRow(newUUID, emailId, chatId, addressId, participantType)
    } yield numberOfInsertedRows

  /**
   * Method that transforms an instance of the CreateChatDTO class into an instance of Chat
   * @param chat instance of the class CreateChatDTO
   * @return an instance of class Chat
   */
  private[implementations] def fromCreateChatDTOtoChat(chat: CreateChatDTO): Chat = {
    val email = chat.email

    Chat(
      chatId = chat.chatId.getOrElse(""),
      subject = chat.subject.getOrElse(""),
      addresses = email.from.toSet ++ email.to.getOrElse(Set()) ++ email.bcc.getOrElse(Set()) ++ email.cc.getOrElse(Set()),
      overseers = Set(),
      emails = Seq(
        Email(
          emailId = email.emailId.getOrElse(""),
          from = email.from.getOrElse(""),
          to = email.to.getOrElse(Set()),
          bcc = email.bcc.getOrElse(Set()),
          cc = email.cc.getOrElse(Set()),
          body = email.body.getOrElse(""),
          date = email.date.getOrElse(""),
          sent = 0,
          attachments = Set())))
  }

  private[implementations] def fromUpsertEmailDTOtoEmail(upsertEmail: UpsertEmailDTO): Email = {
    Email(
      emailId = upsertEmail.emailId.getOrElse(""),
      from = upsertEmail.from.getOrElse(""),
      to = upsertEmail.to.getOrElse(Set()),
      bcc = upsertEmail.bcc.getOrElse(Set()),
      cc = upsertEmail.cc.getOrElse(Set()),
      body = upsertEmail.body.getOrElse(""),
      date = upsertEmail.date.getOrElse(""),
      sent = 0,
      attachments = Set())
  }

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
      __ <- ChatsTable.all.filter(_.chatId === chatId)
      participantType <- EmailAddressesTable.all.filter(_.addressId === addressId).map(_.participantType)
    } yield participantType).exists.result

  //region getChat auxiliary methods

  /**
   * Takes a chat and user, and creates a DBIOAction that gives the chat's id the subject and the user's address
   * @param chatId The chat's id
   * @param userId The user's id
   * @return A DBIOAction that returns the chat's id, it's subject and the user's address
   */
  private[implementations] def getChatDataAction(chatId: String, userId: String) =
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
   *   OR if the user is overseeing another user in the chat (has access to the same emails the oversee has,
   *   excluding the oversee's drafts)
   * - AND if email is draft (sent = 0), only the user with the "from" address can see it
   * @param chatId ID of the requested chat
   * @param userId ID of the user that requested the chat
   * @return for each email, returns a tuple (emailId, body, date, sent)
   */
  private def getEmailsQuery(chatId: String, userId: String) =
    getVisibleEmailsQuery(userId)
      .filter(_._1 === chatId)
      .map {
        case (_, emailId, body, date, sent) => (emailId, body, date, sent)
      }.sortBy { case (emailId, _, date, _) => (date, emailId) }

  /**
   * Method that, given the emailIds of the emails that the a user can see, for each participant of an email,
   * returns a tuple with (emailId, participantType, address).
   *
   * Note that a user will not see a 'bcc' participant unless the user (or one of their oversees) is either
   * the 'bcc' in question or the person who sent the email
   *
   * @param userId ID of the user
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
        .filter(ea => ea.emailId === emailId && ea.participantType === "from")
        .map(_.addressId)

      (participantType, addressId, address) <- EmailAddressesTable.all.join(AddressesTable.all)
        .on((emailAddressRow, addressRow) => {
          val userOverseesOfThisChat = getUserChatOverseesQuery(userId, emailAddressRow.chatId)
          emailAddressRow.emailId === emailId &&
            emailAddressRow.addressId === addressRow.addressId &&
            (emailAddressRow.participantType =!= "bcc" ||
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
   * involved in the chat requested, and the sequence of all the emails of the chat that the user can see
   * @param chatId ID of the requested chat
   * @param userId ID of the user requesting the chat
   * @return A DBIOAction that returns the tuple (chatEmailAddresses, sequenceOfEmailDTOs)
   */
  private def getGroupedEmailsAndAddresses(chatId: String, userId: String) = {
    // Query to get all the emails of this chat that the user can see
    val emailsQuery = getEmailsQuery(chatId, userId)

    // Query to get all the addresses involved in the emails of this chat
    // (emailId, participationType, address)
    val emailAddressesQuery = getEmailAddressesQuery(userId, emailsQuery.map(_._1))

    for {
      emails <- emailsQuery.result
      emailAddressesResult <- emailAddressesQuery.result

      groupedEmailAddresses = emailAddressesResult
        .groupBy(emailAddress => (emailAddress._1, emailAddress._2))
        //group by email ID and receiver type (from, to, bcc, cc)
        .mapValues(_.map(_._3)) // Map: (emailId, receiverType) -> addresses

      // All addresses that sent and received emails in this chat
      chatAddressesResult = emailAddressesResult.map(_._3).distinct

      attachments <- getEmailsAttachments(emailsQuery.map(_._1))

    } yield (chatAddressesResult.toSet, buildEmailDto(emails, groupedEmailAddresses, attachments))
  }

  /**
   * Creates a DBIOAction that retrieves all the IDs of the attachments of each email of the chat
   * @param emailsIds query with the IDs of the emails the user is allowed to see
   * @return A DBIOAction with a Map with the attachment IDs grouped by email ID
   */
  private def getEmailsAttachments(emailsIds: Query[Rep[String], String, scala.Seq]) =
    AttachmentsTable.all
      .filter(_.emailId in emailsIds)
      .map(attachment => (attachment.emailId, attachment.attachmentId)).result
      .map(_
        .groupBy(_._1)
        .mapValues(_.map(_._2)))

  /**
   * Method that links and merges the emails with its addresses and attachments
   * @param emails Sequence of email tuples with (emailId, body, date, sent)
   * @param addresses Map of the email addresses grouped by email ID and participant Type
   * @param attachments Map of the attachment IDs grouped by email ID
   * @return a Sequence of Email(emailId, from, to, bcc, cc, body, date, sent, attachments) DTOs
   */
  private def buildEmailDto(
    emails: Seq[(String, String, String, Int)],
    addresses: Map[(String, String), Seq[String]],
    attachments: Map[String, Seq[String]]): Seq[Email] = {

    emails.map {
      case (emailId, body, date, sent) =>
        Email(
          emailId,
          addresses.getOrElse((emailId, "from"), Seq()).head,
          addresses.getOrElse((emailId, "to"), Seq()).toSet,
          addresses.getOrElse((emailId, "bcc"), Seq()).toSet,
          addresses.getOrElse((emailId, "cc"), Seq()).toSet,
          body,
          date,
          sent,
          attachments.getOrElse(emailId, Seq()).toSet)
    }
  }

  /**
   * Creates a DBIOAction that retrieves all the overseers of a specific chat
   * grouped by the user who gave the oversight permission
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
        .groupBy(_._1) // group by user
        .mapValues(_.map(_._2).toSet)
        .toSeq
        .map(Overseers.tupled)
        .toSet)
  }
  //endregion
  //endregion

}