package repositories.slick.implementations

import javax.inject.Inject
import model.types.Mailbox
import model.types.Mailbox._
import repositories.ChatsRepository
import repositories.slick.mappings._
import repositories.dtos.{ Chat, Email, Overseer, ChatPreview }
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }

class SlickChatsRepository @Inject() (db: Database)(implicit executionContext: ExecutionContext)
  extends ChatsRepository {

  val PREVIEW_BODY_LENGTH: Int = 30

  
/*** Shared auxiliary methods: ***/

  /**
   * This query returns, for a user, all of its chats and for EACH chat, all of its emails
   * (without body) and for each email, all of its participants
   * @param userId Id of the user
   * @param optBox Optional Mailbox specification. If used, a chat will only be shown if the user has it inside
   *               the specified mailbox.
   * @return Query: For a user, all of its chats and for EACH chat, all of its emails
   * (without body) and for each email, all of its participants
   */
  private def getChatsMetadataQueryByUserId(userId: Int, optBox: Option[Mailbox] = None) = {
    for {
      chatId <- UserChatsTable.all.filter(userChatRow =>
        userChatRow.userId === userId &&
          (optBox match {
            case Some(Inbox) => userChatRow.inbox === 1
            case Some(Sent) => userChatRow.sent === 1
            case Some(Trash) => userChatRow.trash === 1
            case Some(Draft) => userChatRow.draft === 1
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
  private def getUserChatOverseesQuery(userId: Int, chatId: Rep[Int]) = {
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
  private def getVisibleEmailsQuery(userId: Int, optBox: Option[Mailbox] = None) =
    for {
      userAddressId <- UsersTable.all.filter(_.userId === userId).map(_.addressId)

      (chatId, emailId, body, date, sent) <- getChatsMetadataQueryByUserId(userId, optBox).filter {
        case (chatId, emailId, body, date, sent, addressId, participantType) =>
          (addressId === userAddressId || addressId.in(getUserChatOverseesQuery(userId, chatId))) &&
            (sent === 1 || (participantType === "from" && addressId === userAddressId))
      }.map(filteredRow => (filteredRow._1, filteredRow._2, filteredRow._3, filteredRow._4, filteredRow._5))

    } yield (chatId, emailId, body, date, sent)

/*** End of Shared auxiliary methods: ***/

  /**
   * Method that returns a preview of all the chats of a given user in a given Mailbox
   * @param mailbox The mailbox being seen
   * @param userId The userId of the user in question
   * @return A Future sequence of ChatPreview dtos. The preview of each chat only shows the most recent email
   */
  def getChatsPreview(mailbox: Mailbox, userId: Int): Future[Seq[ChatPreview]] = {

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

    val resultOption = db.run(chatPreviewQuery.sortBy(_._1.desc).result)

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
  def getChat(chatId: Int, userId: Int): Future[Option[Chat]] = {

    /**
     * Method to get data of a specific chat
     * @param chatId ID of chat
     * @return chat's data. In this case, just the subject
     */
    def getChatData(chatId: Int): Future[Option[(Int, String)]] = {
      val query = ChatsTable.all.filter(_.chatId === chatId).map(row => (row.chatId, row.subject))
      db.run(query.result.headOption)
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
    def getEmailsQuery(chatId: Int, userId: Int) = {
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
    def getEmailAddressesQuery(userId: Int, emailIdsQuery: Query[Rep[Int], Int, scala.Seq]) = {
      val userAddressIdQuery = UsersTable.all.filter(_.userId === userId).map(_.addressId)

      for {
        userAddressId <- userAddressIdQuery
        emailId <- emailIdsQuery
        //from address of this email
        fromAddressId <- EmailAddressesTable.all
          .filter(ea => ea.emailId === emailId && ea.participantType === "from")
          .map(_.addressId)

        (participantType, addressId, address) <- EmailAddressesTable.all.join(AddressesTable.all)
          .on((emailAddressRow, addressRow) =>
            emailAddressRow.emailId === emailId &&
              emailAddressRow.addressId === addressRow.addressId &&
              (emailAddressRow.participantType =!= "bcc" ||
                (emailAddressRow.addressId === userAddressId || fromAddressId === userAddressId)))
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
    def groupEmailsAndAddresses(
      emailsQuery: Query[(Rep[Int], Rep[String], Rep[String], Rep[Int]), (Int, String, String, Int), scala.Seq],
      emailAddressesQuery: Query[(Rep[Int], Rep[String], Rep[String]), (Int, String, String), scala.Seq]): Future[(Seq[String], scala.Seq[Email])] = {

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
        chatAddresses,
        buildEmailDto(emails, groupedAddresses, attachments))
    }

    /**
     * Method that retrieves all the email addresses (that the user is allow to see) involved in the chat requested
     * and the sequence of all the emails of the chat that the user can see
     * @param chatId ID of the requested chat
     * @param userId ID of the user requesting the chat
     * @return a Future of the tuple (chatEmailAddresses, sequenceOfEmailDTOs)
     */
    def getGroupedEmailsAndAddresses(chatId: Int, userId: Int): Future[(Seq[String], Seq[Email])] = {
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
    def getEmailsAttachments(emailsIds: Query[Rep[Int], Int, scala.Seq]) = {
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
    def buildEmailDto(
      emails: Seq[(Int, String, String, Int)],
      addresses: Map[(Int, String), Seq[String]],
      attachments: Map[Int, Seq[Int]]): Seq[Email] = {

      emails.map {
        case (emailId, body, date, sent) =>
          Email(
            emailId,
            addresses.getOrElse((emailId, "from"), Seq()).head,
            addresses.getOrElse((emailId, "to"), Seq()),
            addresses.getOrElse((emailId, "bcc"), Seq()),
            addresses.getOrElse((emailId, "cc"), Seq()),
            body,
            date,
            sent,
            attachments.getOrElse(emailId, Seq()))
      }
    }

    /**
     * Method that retrieves all the overseers of a specific chat grouped by the user who gave the oversight permission
     * @param chatId ID of the requested chat
     * @return a Future with a Sequence of Overseer(userAddress, overseersAddresses) DTOs
     */
    def getOverseersData(chatId: Int) = {
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
          .mapValues(_.map(_._2))
          .toSeq
          .map(Overseer.tupled))
    }

    for {
      chatData <- getChatData(chatId)
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

}