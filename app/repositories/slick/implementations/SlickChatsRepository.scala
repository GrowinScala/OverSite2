package repositories.slick.implementations

import java.util.UUID

import javax.inject.Inject
import model.dtos.{ CreateChatDTO, CreateEmailDTO }
import model.types.Mailbox
import model.types.Mailbox._
import repositories.ChatsRepository
import repositories.slick.mappings._
import repositories.dtos._
import slick.jdbc.MySQLProfile.api._
import utils.DateUtils

import scala.concurrent.{ ExecutionContext, Future }

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
   * Method that retrieves the emails that a specific user can see:
   * @param userId The user in question
   * @param optBox Optional filter for a given mailbox
   * @return The emails that a specific user can see:
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
   * Method that returns a preview of all the chats of a given user in a given Mailbox
   * @param mailbox The mailbox being seen
   * @param userId The userId of the user in question
   * @return A Future sequence of ChatPreview dtos. The preview of each chat only shows the most recent email
   */
  def getChatsPreview(mailbox: Mailbox, userId: String): Future[Seq[ChatPreview]] = {

    val groupedQuery = getVisibleEmailsQuery(userId, Some(mailbox))
      .map(visibleEmailRow => (visibleEmailRow._1, visibleEmailRow._4))
      .groupBy(_._1)
      .map { case (chatId, date) => (chatId, date.map(_._2).max) }

    val chatPreviewQuery = for {
      (chatId, date) <- groupedQuery
      subject <- ChatsTable.all.filter(_.chatId === chatId).map(_.subject)
      (emailId, body) <- EmailsTable.all.filter(emailRow =>
        emailRow.chatId === chatId && emailRow.date === date).map(emailRow =>
        (emailRow.emailId, emailRow.body.take(PREVIEW_BODY_LENGTH)))
      addressId <- EmailAddressesTable.all.filter(emailAddressRow =>
        emailAddressRow.emailId === emailId && emailAddressRow.participantType === "from")
        .map(_.addressId)
      address <- AddressesTable.all.filter(_.addressId === addressId).map(_.address)
    } yield (chatId, subject, address, date, body)

    val resultOption = db.run(chatPreviewQuery.sortBy(_._4.desc).result)

    val result = resultOption.map(_.map {
      case (chatId, subject, address, dateOption, body) =>
        (chatId, subject, address, dateOption.getOrElse("Missing Date"), body)
    })

    result.map(_.map(ChatPreview.tupled))

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
  def getChat(chatId: String, userId: String): Future[Option[Chat]] = {

    for {
      chatData <- getChatData(chatId, userId)
      (addresses, emails) <- getGroupedEmailsAndAddresses(chatId, userId)
      overseers <- getOverseersData(chatId)
    } yield chatData.map {
      case (id, subject) =>
        Chat(
          id,
          subject,
          addresses,
          overseers,
          emails)
    }

  }

  def postChat(createChatDTO: CreateChatDTO, userId: String): Future[CreateChatDTO] = {
    //TODO AFTER AUTHENTICATION MERGE: delete userId and only receive user address through the "from" field of the createChatDTO
    val emailDTO = createChatDTO.email
    val date = DateUtils.getCurrentDate

    /** Generate chatId, userChatId and emailId **/
    val chatId = UUID.randomUUID().toString
    val userChatId = UUID.randomUUID().toString
    val emailId = UUID.randomUUID().toString

    val inserts = for {
      _ <- ChatsTable.all += ChatRow(chatId, createChatDTO.subject.getOrElse(""))
      _ <- UserChatsTable.all += UserChatRow(userChatId, userId, chatId, 0, 0, 1, 0)

      _ <- insertEmail(emailDTO, chatId, emailId, date)

    } yield ()

    db.run(inserts.transactionally).map(_ =>
      createChatDTO.copy(chatId = Some(chatId), email = emailDTO.copy(emailId = Some(emailId), date = Some(date))))
  }

  def postEmail(createEmailDTO: CreateEmailDTO, chatId: String, userId: String): Future[CreateChatDTO] = {
    val date = DateUtils.getCurrentDate

    val emailId = UUID.randomUUID().toString

    val insertAndUpdate = for {
      //TODO explodes if chat does not exist
      chat <- (for { chatQuery <- getChatDataQuery(chatId, userId) } yield chatQuery).result.headOption

      _ <- insertEmail(createEmailDTO, chatId, emailId, date)

      _ <- UserChatsTable.updateDraftField(userId, chatId, 1)
    } yield chat //(chatId, subject)

    for {
      chat <- db.run(insertAndUpdate.transactionally)
    } yield CreateChatDTO(
      chatId = chat.map(_._1),
      subject = chat.map(_._2),
      CreateEmailDTO(
        emailId = Some(emailId),
        from = createEmailDTO.from,
        to = createEmailDTO.to,
        bcc = createEmailDTO.bcc,
        cc = createEmailDTO.cc,
        body = createEmailDTO.body,
        date = Some(date)))
  }

  private[implementations] def insertEmail(createEmailDTO: CreateEmailDTO, chatId: String, emailId: String, date: String) = {
    for {
      _ <- EmailsTable.all += EmailRow(emailId, chatId, createEmailDTO.body.getOrElse(""), date, 0)

      fromInsert = insertEmailAddressIfNotExists(emailId, chatId, insertAddressIfNotExists(createEmailDTO.from), "from")
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
        .getOrElse(AddressRow(UUID.randomUUID().toString, address))

      _ <- AddressesTable.all.insertOrUpdate(row)
    } yield row.addressId
  }

  private[implementations] def insertEmailAddressIfNotExists(emailId: String, chatId: String, address: DBIO[String], participantType: String) /*: DBIO[String]*/ =
    for {
      addressId <- address
      existing <- EmailAddressesTable.selectByEmailIdAddressAndType(emailId, addressId, participantType)
        .result.headOption

      row = existing
        .getOrElse(EmailAddressRow(UUID.randomUUID().toString, emailId, chatId, addressId, participantType))

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
   * Method to get data of a specific chat
   * @param chatId ID of chat
   * @return chat's data. In this case, just the subject
   */
  private[implementations] def getChatData(chatId: String, userId: String): Future[Option[(String, String)]] = {
    val query = getChatDataQuery(chatId, userId)
    db.run(query.result.headOption)
  }

  //TODO Document this
  private[implementations] def getChatDataQuery(chatId: String, userId: String): Query[(Rep[String], Rep[String]), (String, String), scala.Seq] = {
    ChatsTable.all
      .join(UserChatsTable.all)
      .on((chat, userChat) => chat.chatId === chatId && chat.chatId === userChat.chatId && userChat.userId === userId)
      .map { case (chat, _) => (chat.chatId, chat.subject) }
  }

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
   * Method that, given the emailIds of the emails the user can see
   * @param userId ID of the user requesting a chat
   * @param emailIdsQuery query with the email IDs of the chat to show in the response, already filtered
   *                      by the email the user has permission to see (including its oversees)
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
   * Method that takes the queries to get the emails and the email addresses,
   * groups the data by email and builds an Email DTO
   * @param emailsQuery query to get the chat's emails that the user can see
   * @param emailAddressesQuery query to get the email addresses involved in each email
   * @return all the addresses of the chat
   *         and a Seq of Email DTOs
   */
  private def groupEmailsAndAddresses(
    emailsQuery: Query[(Rep[String], Rep[String], Rep[String], Rep[Int]), (String, String, String, Int), scala.Seq],
    emailAddressesQuery: Query[(Rep[String], Rep[String], Rep[String]), (String, String, String), scala.Seq]): Future[(Set[String], scala.Seq[Email])] = {

    /** Emails **/
    val emails = db.run(emailsQuery.result)
    /** Email Addresses **/
    val emailAddressesResult = db.run(emailAddressesQuery.result)
    val groupedEmailAddresses =
      emailAddressesResult
        .map(_
          .groupBy(emailAddress => (emailAddress._1, emailAddress._2)) //group by email ID and receiver type (from, to, bcc, cc)
          .mapValues(_.map(_._3)) // Map: (emailId, receiverType) -> addresses
        )
    /** Chat Addresses **/
    // All addresses that sent and received emails in this chat
    val chatAddressesResult = emailAddressesResult.map(_.map(_._3).distinct)
    /** Attachments **/
    val attachments = getEmailsAttachments(emailsQuery.map(_._1))

    /** Futures: **/
    for {
      chatAddresses <- chatAddressesResult
      emails <- emails
      groupedAddresses <- groupedEmailAddresses
      attachments <- attachments
    } yield (
      chatAddresses.toSet,
      buildEmailDto(emails, groupedAddresses, attachments))
  }

  /**
   * Method that retrieves all the email addresses (that the user is allow to see) involved in the chat requested
   * and the sequence of all the emails of the chat that the user can see
   * @param chatId ID of the requested chat
   * @param userId ID of the user requesting the chat
   * @return a Future of the tuple (chatEmailAddresses, sequenceOfEmailDTOs)
   */
  private def getGroupedEmailsAndAddresses(chatId: String, userId: String): Future[(Set[String], Seq[Email])] = {
    // Query to get all the emails of this chat that the user can see
    val emailsQuery = getEmailsQuery(chatId, userId)

    // Query to get all the addresses involved in the emails of this chat
    // (emailId, participationType, address)
    val emailAddressesQuery = getEmailAddressesQuery(userId, emailsQuery.map(_._1))

    groupEmailsAndAddresses(emailsQuery, emailAddressesQuery)
  }

  /**
   * Method that retrieves all the IDs of the attachments of each email of the chat
   * @param emailsIds query with the IDs of the emails the user is allowed to see
   * @return a Future with a Map with the attachment IDs grouped by email ID
   */
  private def getEmailsAttachments(emailsIds: Query[Rep[String], String, scala.Seq]) = {
    val query = AttachmentsTable.all
      .filter(_.emailId in emailsIds)
      .map(attachment => (attachment.emailId, attachment.attachmentId))
    // (emailId, attachmentId)

    db.run(query.result)
      .map(_
        .groupBy(_._1)
        .mapValues(_.map(_._2)))
  }

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
   * Method that retrieves all the overseers of a specific chat grouped by the user who gave the oversight permission
   * @param chatId ID of the requested chat
   * @return a Future with a Sequence of Overseer(userAddress, overseersAddresses) DTOs
   */
  private def getOverseersData(chatId: String) = {
    val chatOverseersQuery = for {
      (overseerId, overseeId) <- OversightsTable.all.filter(_.chatId === chatId).map(row => (row.overseerId, row.overseeId))

      overseeAddressId <- UsersTable.all.filter(_.userId === overseeId).map(_.addressId)
      overseeAddress <- AddressesTable.all.filter(_.addressId === overseeAddressId)
        .map(_.address)

      overseerAddressId <- UsersTable.all.filter(_.userId === overseerId).map(_.addressId)
      overseerAddress <- AddressesTable.all.filter(_.addressId === overseerAddressId)
        .map(_.address)
    } yield (overseeAddress, overseerAddress)

    db.run(chatOverseersQuery.result)
      .map(_
        .groupBy(_._1) // group by user
        .mapValues(_.map(_._2).toSet)
        .toSeq
        .map(Overseers.tupled)
        .toSet)
  }
  //endregion

}