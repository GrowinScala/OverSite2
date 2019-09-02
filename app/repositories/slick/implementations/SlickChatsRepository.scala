package repositories.slick.implementations

import javax.inject.Inject
import model.dtos.{ CreateChatDTO, CreateEmailDTO }
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
            case Some(Drafts) => userChatRow.draft === 1
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
    for {
      userAddressId <- UsersTable.all.filter(_.userId === userId).map(_.addressId)

      (chatId, emailId, body, date, sent) <- getChatsMetadataQueryByUserId(userId, optBox).filter {
        case (chatId, emailId, body, date, sent, addressId, participantType) =>
          (addressId === userAddressId || addressId.in(getUserChatOverseesQuery(userId, chatId))) &&
            (sent === 1 || (participantType === "from" && addressId === userAddressId))
      }.map(filteredRow => (filteredRow._1, filteredRow._2, filteredRow._3, filteredRow._4, filteredRow._5))

    } yield (chatId, emailId, body, date, sent)
  //endregion

  /**
   * Creates a DBIOAction that returns a preview of all the chats of a given user in a given Mailbox
   * @param mailbox The mailbox being seen
   * @param userId The userId of the user in question
   * @return A DBIOAction that when run returns a sequence of ChatPreview dtos.
   *         The preview of each chat only shows the most recent email
   */
  private[implementations] def getChatsPreviewAction(mailbox: Mailbox, userId: String) = {

    println("THIS IS THE USER GOING TO THE GETCHATSPREVIEW ALONG WITH THE MAILBOX", userId, mailbox)
    println("THIS IS THE USER_CHATS_TABLE BEFORE THE GETCHATSPREVIEW", Await.result(db.run(UserChatsTable.all.result), Duration.Inf),
      "THIS IS THE END OF THE USER_CHATS_TABLE")
    println("THIS IS THE EMAIL_ADDRESS_TABLE", Await.result(db.run(EmailAddressesTable.all.result), Duration.Inf),
      "THIS IS THE END OF THE EMAIL_ADDRESS_TABLE")
    println("THIS IS THE ADDRESS_TABLE", Await.result(db.run(AddressesTable.all.result), Duration.Inf),
      "THIS IS THE END OF THE ADDRESS_TABLE")
    println("THIS IS THE EMAILS_TABLE", Await.result(db.run(EmailsTable.all.sortBy(_.chatId).result), Duration.Inf),
      "THIS IS THE END OF THE EMAIL_ADDRESS_TABLE")

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
      }.distinctOn(_._1)

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

      if emailId in visibleEmailsQuery.map(_._2)

    } yield (chatId, subject, address, date, body)

    chatPreviewQuery
      .sortBy(chatpreview => (chatpreview._4.desc, chatpreview._5.asc, chatpreview._3.asc))
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
    // println("THIS IS THE RESULT BEFORE CLEANING", Await.result(result, Duration.Inf))

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
  private[implementations] def getChatAction(chatId: String, userId: String) = {

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

  private[implementations] def postChatAction(createChatDTO: CreateChatDTO, userId: String) = {
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
      fromAddress <- UsersTable.all.filter(_.userId === userId).join(AddressesTable.all)
        .on(_.addressId === _.addressId).map(_._2.address).result.head

      _ <- insertEmail(emailDTO, chatId, emailId, fromAddress, date)

    } yield ()

    inserts.map(_ =>
      createChatDTO.copy(chatId = Some(chatId), email = emailDTO.copy(emailId = Some(emailId), date = Some(date))))
  }

  def postChat(createChatDTO: CreateChatDTO, userId: String): Future[CreateChatDTO] =
    db.run(postChatAction(createChatDTO, userId).transactionally)

  private[implementations] def postEmailAction(createEmailDTO: CreateEmailDTO, chatId: String, userId: String) = {
    val date = DateUtils.getCurrentDate

    val emailId = newUUID

    val insertAndUpdate = for {
      chatAddress <- getChatDataAction(chatId, userId)

      _ <- chatAddress match {
        case Some((chatID, subject, fromAddress)) => insertEmail(createEmailDTO, chatId, emailId, fromAddress, date)
          .andThen(UserChatsTable.updateDraftField(userId, chatId, 1))
        case None => DBIOAction.successful(None)
      }

    } yield chatAddress

    insertAndUpdate.map {
      case Some(tuple) => tuple match {
        case (chatID, subject, fromAddress) => Some(CreateChatDTO(
          Some(chatID),
          Some(subject),
          CreateEmailDTO(
            emailId = Some(emailId),
            from = fromAddress,
            to = createEmailDTO.to,
            bcc = createEmailDTO.bcc,
            cc = createEmailDTO.cc,
            body = createEmailDTO.body,
            date = Some(date))))
      }
      case None => None
    }
  }

  def postEmail(createEmailDTO: CreateEmailDTO, chatId: String, userId: String): Future[Option[CreateChatDTO]] =
    db.run(postEmailAction(createEmailDTO, chatId, userId).transactionally)

  private[implementations] def moveChatToTrashAction(chatId: String, userId: String) =
    UserChatsTable.moveChatToTrash(userId, chatId)
      .map(_ != 0)

  def moveChatToTrash(chatId: String, userId: String): Future[Boolean] =
    db.run(moveChatToTrashAction(chatId, userId))

  private[implementations] def insertEmail(createEmailDTO: CreateEmailDTO, chatId: String,
    emailId: String, fromAddress: String, date: String) = {
    for {
      _ <- EmailsTable.all += EmailRow(emailId, chatId, createEmailDTO.body.getOrElse(""), date, 0)

      fromInsert = insertEmailAddressIfNotExists(emailId, chatId, insertAddressIfNotExists(fromAddress), "from")
      toInsert = createEmailDTO.to.getOrElse(Set()).map(
        to => insertEmailAddressIfNotExists(emailId, chatId, insertAddressIfNotExists(to), "to"))
      bccInsert = createEmailDTO.bcc.getOrElse(Set()).map(
        bcc => insertEmailAddressIfNotExists(emailId, chatId, insertAddressIfNotExists(bcc), "bcc"))
      ccInsert = createEmailDTO.cc.getOrElse(Set()).map(
        cc => insertEmailAddressIfNotExists(emailId, chatId, insertAddressIfNotExists(cc), "cc"))

      _ <- DBIO.sequence(Vector(fromInsert) ++ toInsert ++ bccInsert ++ ccInsert)
    } yield ()
  }

  private[implementations] def insertAddressIfNotExists(address: String): DBIO[String] = {
    for {
      existing <- AddressesTable.selectByAddress(address).result.headOption

      row = existing
        .getOrElse(AddressRow(newUUID, address))

      _ <- AddressesTable.all.insertOrUpdate(row)
    } yield row.addressId
  }

  private[implementations] def insertEmailAddressIfNotExists(emailId: String, chatId: String, address: DBIO[String], participantType: String) /*: DBIO[String]*/ =
    for {
      addressId <- address
      existing <- EmailAddressesTable.selectByEmailIdAddressAndType(emailId, addressId, participantType)
        .result.headOption

      row = existing
        .getOrElse(EmailAddressRow(newUUID, emailId, chatId, addressId, participantType))

      _ <- EmailAddressesTable.all.insertOrUpdate(row)
    } yield ()

  private[implementations] def fromCreateChatDTOtoChatDTO(chat: CreateChatDTO): Chat = {
    val email = chat.email

    Chat(
      chatId = chat.chatId.getOrElse(""),
      subject = chat.subject.getOrElse(""),
      addresses = Set(email.from) ++ email.to.getOrElse(Set()) ++ email.bcc.getOrElse(Set()) ++ email.cc.getOrElse(Set()),
      overseers = Set(),
      emails = Seq(
        Email(
          emailId = email.emailId.getOrElse(""),
          from = email.from,
          to = email.to.getOrElse(Set()),
          bcc = email.bcc.getOrElse(Set()),
          cc = email.cc.getOrElse(Set()),
          body = email.body.getOrElse(""),
          date = email.date.getOrElse(""),
          sent = 0,
          attachments = Set())))
  }

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
      _ <- UserChatsTable.all.filter(userChatRow => userChatRow.chatId === chatId && userChatRow.userId === userId)
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
  private def getEmailsQuery(chatId: String, userId: String) = {
    val query = getVisibleEmailsQuery(userId)
      .filter(_._1 === chatId)
      .map {
        case (_, emailId, body, date, sent) => (emailId, body, date, sent)
      }

    // Because for chats where a user is participant and is also overseeing another user (or more than one),
    // the emails would be repeated
    query.distinct
  }

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
  private def getOverseersData(chatId: String) = {
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

}