package repositories.slick.implementations

import javax.inject.Inject
import model.types.Mailbox
import model.types.Mailbox._
import play.api.db.slick.{ DatabaseConfigProvider, HasDatabaseConfigProvider }
import play.db.NamedDatabase
import repositories.ChatsRepository
import repositories.slick.mappings._
import repositories.dtos.{ Chat, Email, Overseer, ChatPreview }
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }

class SlickChatsRepository @Inject() (@NamedDatabase("oversitedb") protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
  extends ChatsRepository with HasDatabaseConfigProvider[JdbcProfile] {

  val PREVIEW_BODY_LENGTH: Int = 30

/*** Stuff that I need: ***/

  /**
   * Method to get data of a specific chat
   * @param chatId ID of chat
   * @return chat's data. In this case, the subject
   */
  private def getChatData(chatId: Int): Future[String] = {
    val query = ChatsTable.all.filter(_.chatId === chatId).map(_.subject)
    db.run(query.result.head) // TODO headOption (chat might not exist)
  }

  //TODO: Document this
  private def getChatsMetadataQueryByUserId(userId: Int, optBox: Option[Mailbox] = None) = {
    // This query returns, for a user, all of its chats and for EACH chat, all of its emails
    // (without body) and for each email, all of its participants
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

      (emailId, date, sent) <- EmailsTable.all.filter(_.chatId === chatId).map(
        emailRow => (emailRow.emailId, emailRow.date, emailRow.sent))

      (addressId, participantType) <- EmailAddressesTable.all.filter(_.emailId === emailId)
        .map(emailAddressRow =>
          (emailAddressRow.addressId, emailAddressRow.participantType))

    } yield (chatId, emailId, date, sent, addressId, participantType)
  }

  // TODO Document this
  private def getGroupedEmailsAndAddresses(chatId: Int, userId: Int): Future[(Seq[String], Seq[Email])] = {

    /** Queries **/
    val userAddressIdQuery = UsersTable.all.filter(_.userId === userId).map(_.addressId)

    // All chat metadata
    val getChatEmailsQuery = getChatsMetadataQueryByUserId(userId, None).filter(_._1 === chatId)

    // Get all oversees of this user for this particular chat
    val userOverseesAddressIdQuery = getUserChatOverseesQuery(userId, Some(chatId))

    // Query to filter the chat's emails
    val emailsQuery = for {
      userAddressId <- userAddressIdQuery

      // If the address is the user's OR If the address is an oversee of the user
      // AND if the email is a draft (sent == 0), the "from" address must be the user's
      (emailId, date, sent) <- getChatEmailsQuery.filter {
        case (chatId, emailId, date, sent, addressId, participantType) =>
          (addressId === userAddressId || addressId.in(userOverseesAddressIdQuery)) &&
            (sent === 1 || (participantType === "from" && addressId === userAddressId))
      }
        .map(filteredRow => (filteredRow._2, filteredRow._3, filteredRow._4))

      body <- EmailsTable.all.filter(_.emailId === emailId).map(_.body)

    } yield (emailId, body, date, sent)

    // Query to get all the addresses involved in the emails of this chat
    // (emailId, participationType, address)
    val emailAddressesQuery = getEmailAddressesQuery(userAddressIdQuery, emailsQuery.map(_._1))

    /** Results (Futures) **/

    /** Emails **/
    val emails = db.run(emailsQuery.distinct.result)
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

    for {
      chatAddresses <- chatAddressesResult
      emails <- emails
      groupedAddresses <- groupedEmailAddresses
      attachments <- attachments
    } yield (
      chatAddresses,
      buildEmailDto(emails, groupedAddresses, attachments))

  }

  private def getEmailAddressesQuery(userAddressIdQuery: Query[Rep[Int], Int, scala.Seq], emailIdsQuery: Query[Rep[Int], Int, scala.Seq]) = {
    //Query with fromAddressId for each emailId
    val fromAddressIdsQuery = EmailAddressesTable.all
      .filter(emailAddressRow =>
        emailAddressRow.emailId.in(emailIdsQuery) &&
          emailAddressRow.participantType === "from")
      .map(emailAddressRow => (emailAddressRow.emailId, emailAddressRow.addressId))

    for {
      userAddressId <- userAddressIdQuery
      emailId <- emailIdsQuery
      /*fromAddressId <- EmailAddressesTable.all
        .filter(ea => ea.emailId === emailId && ea.participantType === "from")
        .map(_.addressId)*/
      // TODO this or that
      fromAddressId <- fromAddressIdsQuery.filter(_._1 === emailId).map(_._2)

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

  // TODO igual ao João (com mais um filtro). Fazer metodo geral?
  private def getUserChatOverseesQuery(userId: Int, chatId: Option[Int] = None): Query[Rep[Int], Int, scala.Seq] = {
    val chatFilteredOversightsTable = OversightsTable.all.filter(
      row => chatId match {
        case Some(id) => row.chatId === id
        case _ => true: Rep[Boolean]
      })

    UsersTable.all
      .join(chatFilteredOversightsTable)
      .on((userRow, oversightRow) =>
        userRow.userId === oversightRow.overseeId &&
          oversightRow.overseerId === userId)
      .map(_._1.addressId)
  }

  private def getEmailsAttachments(emailsIds: Query[Rep[Int], Int, scala.Seq]) = {
    val query = AttachmentsTable.all
      .filter(_.emailId in emailsIds)
      .map(attachment => (attachment.emailId, attachment.attachmentId))
    // (emailId, attachmentId)

    db.run(query.result)
      .map(_
        .groupBy(_._1)
        .mapValues(_.map(_._2)))
  }

  private def buildEmailDto(
    emails: Seq[(Int, String, String, Int)],
    addresses: Map[(Int, String), Seq[String]],
    attachments: Map[Int, Seq[Int]]): Seq[Email] = {

    emails.map {
      case (emailId, body, date, sent) =>
        Email(
          emailId,
          addresses.getOrElse((emailId, "from"), Seq()).head,
          addresses.getOrElse((emailId, "to"), Seq()).distinct, //TODO distinct?
          addresses.getOrElse((emailId, "bcc"), Seq()).distinct,
          addresses.getOrElse((emailId, "cc"), Seq()).distinct,
          body,
          date,
          sent,
          attachments.getOrElse(emailId, Seq()))
    }
  }
  /**
   * Query that retrieves all tuples of AddressId/Address a certain user is able to see emails from in a certain chat.
   * @param overseerUserId the userId of self
   * @param chatId the chatId we're interested on
   * @return a Seq of Tuples
   */
  private def getOverseerAddresses(overseerUserId: Int, chatId: Int): Future[Seq[(Int, String)]] = {
    val overseesUserIds = OversightsTable.all.filter(o => o.overseerId === overseerUserId && o.chatId === chatId).map(_.overseeId)
    val addressIds = UsersTable.all.filter(user => user.userId.in(overseesUserIds) || user.userId === overseerUserId)
      .join(AddressesTable.all).on(_.addressId === _.addressId).map(tp => (tp._2.addressId, tp._2.address))

    db.run(addressIds.result)
  }
  /*
   overseeAddress <- UsersTable.all.join(AddressesTable.all)
     .on((user, address) => user.userId === oversight.overseeId && address.addressId === user.addressId)
     .map(_._2.address)
   overseerAddress <- UsersTable.all.join(AddressesTable.all)
     .on((user, address) => user.userId === oversight.overseerId && address.addressId === user.addressId)
     .map(_._2.address)
   */
  private def getOverseersData(chatId: Int) = {
    val chatOverseersQuery = for {
      oversight <- OversightsTable.all.filter(_.chatId === chatId)

      overseeAddressId <- UsersTable.all.filter(_.userId === oversight.overseeId).map(_.addressId)
      overseeAddress <- AddressesTable.all.filter(_.addressId === overseeAddressId)
        .map(_.address)

      overseerAddressId <- UsersTable.all.filter(_.userId === oversight.overseerId).map(_.addressId)
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

/*** End of Stuff that I need: ***/

/*** Chat ***/
  def getChat(chatId: Int, userId: Int): Future[Chat] = {
    for {
      subject <- getChatData(chatId)
      (addresses, emails) <- getGroupedEmailsAndAddresses(chatId, userId)
      overseers <- getOverseersData(chatId)
    } yield Chat(
      chatId,
      subject,
      addresses,
      overseers,
      emails)
  }

/*** Chat Preview ***/
  def getChatPreview(mailbox: Mailbox, userId: Int): Future[Seq[ChatPreview]] = {
    val baseQuery = getChatsMetadataQueryByUserId(userId, Some(mailbox))

    /*
    val overseesAddressIdQuery = UsersTable.all
      .join(OversightsTable.all)
      .on((userRow, oversightRow) => userRow.userId === oversightRow.overseeId && oversightRow.overseerId === userId)
      .map(_._1.addressId)
      */
    val overseesAddressIdQuery = getUserChatOverseesQuery(userId)

    val filteredQuery = for {
      userAddressId <- UsersTable.all.filter(_.userId === userId).map(_.addressId)

      (chatId, date) <- baseQuery.filter {
        case (chatId, emailId, date, sent, addressId, participantType) =>
          (addressId === userAddressId || addressId.in(overseesAddressIdQuery)) &&
            (sent === 1 || (participantType === "from" && addressId === userAddressId))
      }
        .map(filteredRow => (filteredRow._1, filteredRow._3))

    } yield (chatId, date)

    val groupedQuery = filteredQuery.groupBy(_._1).map { case (chatId, date) => (chatId, date.map(_._2).max) }

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
}