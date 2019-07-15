package repositories.slick.implementations

import javax.inject.Inject
import model.types.Mailbox
import model.types.Mailbox._
import play.api.db.slick.{ DatabaseConfigProvider, HasDatabaseConfigProvider }
import play.db.NamedDatabase
import repositories.ChatsRepository
import repositories.slick.mappings._
import repositories.dtos.{Chat, Email, Overseer, ChatPreview}
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }



class SlickChatsRepository @Inject()
(@NamedDatabase("oversitedb") protected val dbConfigProvider: DatabaseConfigProvider)
(implicit executionContext: ExecutionContext)
	extends ChatsRepository with HasDatabaseConfigProvider[JdbcProfile] {

  val PREVIEW_BODY_LENGTH: Int = 30

  /*** Chat ***/
  def getChat(chatId: Int, userId: Int) = {
    for {
      subject <- processChat(chatId)
      (addresses, emails) <- processEmailsAndAddresses(chatId, userId)
      overseers <- processOverseers(chatId)
    } yield
      Chat(
        chatId,
        subject,
        addresses,
        overseers,
        emails
      )
  }

  private def processChat(chatId: Int): Future[String] = {
    val query = ChatsTable.all.filter(_.chatId === chatId).map(_.subject)
    db.run(query.result.head)     // TODO headOption (chat might not exist)
  }

  def getChatPreview(mailbox: Mailbox, userId: Int): Future[Seq[ChatPreview]] = {
    //This query returns a User, returns all of its chats and for EACH chat, all of its emails
    // and for each email, all of it's participants
    val baseQuery = for {
      chatId <- UserChatsTable.all.filter(userChatRow =>
        userChatRow.userId === userId &&
          (mailbox match {
            case Inbox => userChatRow.inbox === 1
            case Sent => userChatRow.sent === 1
            case Trash => userChatRow.trash === 1
            case Draft => userChatRow.draft === 1
          })).map(_.chatId)

      (emailId, date, sent) <- EmailsTable.all.filter(_.chatId === chatId).map(
        emailRow => (emailRow.emailId, emailRow.date, emailRow.sent))

      (addressId, participantType) <- EmailAddressesTable.all.filter(_.emailId === emailId)
        .map(emailAddressRow =>
          (emailAddressRow.addressId, emailAddressRow.participantType))

    } yield (chatId, emailId, date, sent, addressId, participantType)

    val overseesAddressIdQuery = UsersTable.all
      .join(OversightsTable.all)
      .on((userRow, oversightRow) => userRow.userId === oversightRow.overseeId && oversightRow.overseerId === userId)
      .map(_._1.addressId)

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

  /**
    * Query that retrieves all tuples of AddressId/Address a certain user is able to see emails from in a certain chat.
    * @param overseerUserId the userId of self
    * @param chatId the chatId we're interested on
    * @return a Seq of Tuples
    */
  private def getOverseerAdresses(overseerUserId: Int, chatId: Int): Future[Seq[(Int, String)]] = {
    val overseesUserIds = OversightsTable.all.filter(o => o.overseerId === overseerUserId && o.chatId === chatId).map(_.overseeId)
    val addressIds = UsersTable.all.filter(user => user.userId.in(overseesUserIds) || user.userId === overseerUserId)
      .join(AddressesTable.all).on(_.addressId === _.addressId).map(tp => (tp._2.addressId, tp._2.address))

    db.run(addressIds.result)
  }

  /**
    * A query that returns a SET of all emails a certain group of addressIds will be able to see
    * @param chatId
    * @param overseerAddressId
    * @param overseenAddressIds
    * @return
    */
  private def getEmailsAndAddressesQuery(chatId: Int, overseerAddressId: Int, overseenAddressIds: Seq[Int]) = {
    val userAddressId = UsersTable.all.filter(_.userId === overseerAddressId).map(_.addressId)
    val userAddress = AddressesTable.all.filter(_.addressId.in(userAddressId)).map(_.address)

    val emailsQuery = for {
      email <- EmailsTable.all.filter(_.chatId === chatId)
      // Filter the drafts: (the user can only see chat's drafts if the user is sender (from)
      fromAddressId <- EmailAddressesTable.all
        .filter(ea => ea.emailId === email.emailId && ea.participantType === "from")
        .map(_.addressId)
      fromAddress <- AddressesTable.all.filter(_.addressId === fromAddressId).map(_.address)
      if (email.sent === 0 && fromAddress.in(userAddress)) || email.sent === 1
    } yield (email.emailId, email.body, email.date, email.sent)

    val emailAddressesQuery = for {
      emailId <- emailsQuery.map(_._1)
      emailAddress <- EmailAddressesTable.all.filter(_.emailId === emailId)
      address <- AddressesTable.all.filter(_.addressId === emailAddress.addressId)
    } yield (emailAddress.emailId, emailAddress.participantType, address.address)

    (emailsQuery, emailAddressesQuery)
    // ( (emailId, body, date, sent), (emailId, participantType, address) )
  }

  /*** Emails ***/
  private def processEmailsAndAddresses(chatId: Int, userId: Int): Future[(Seq[String], Seq[Email])] = {
    /** User Address **/
    /*
    val userAddressIdQuery = UserChatsTable.all.filter(uc => uc.userId === userId && uc.chatId === chatId)
      .join(UsersTable.all).on(_.userId === _.userId)
      .map(_._2.addressId)
    val userAddressQuery = AddressesTable.all.join(userAddressIdQuery).on(_.addressId === _.value).map(_._1.address)
    //AddressesTable.all.filter(_.addressId.in(userAddressIdQuery)).map(_.address)
    val userAddressResult = db.run(userAddressQuery.result.head)
    /** Overseers **/
    val overseesIdsQuery = OversightsTable.all.filter(o => o.overseerId === userId && o.chatId === chatId).map(_.overseeId)
    val overseesAddressesQuery = UsersTable.all.filter(_.userId.in(overseesIdsQuery)).map(_.addressId)
      .join(AddressesTable.all).on(_.value === _.addressId).map(_._2.address)
    val overseesAddressesResult = db.run(overseesAddressesQuery.result)
         */

    /** All addresses **/
    val res1 = getOverseerAdresses(userId, chatId)

    /** All emails **/





    /** Emails and Email Addresses **/
    val (emailsQuery, emailAddressesQuery) = getEmailsAndAddressesQuery(chatId, userId)
    /** Emails **/
    val emailsResult = db.run(emailsQuery.result)
    val groupedEmails = emailsResult.map(_.groupBy(_._1)) // group by email id
    /** Email Addresses **/
    val emailAddressesResult = db.run(emailAddressesQuery.result)
    val emailAddressesPerEmail =
      emailAddressesResult
        .map(_.groupBy(_._1).mapValues(_.map(_._3)))
    val emailAddressesPerEmailAndType =
      emailAddressesResult
        .map(_
          .groupBy(emailAddress => (emailAddress._1, emailAddress._2))  //group by email ID and receiver type (from, to, bcc, cc)
          .mapValues(_.map(_._3))   // Map: (emailId, receiverType) -> addresses
        )
    /** Chat Addresses **/
    // All addresses that sent and received emails in this chat
    val chatAddressesResult = emailAddressesResult.map(_.map(_._3).distinct)
    /** Attachments **/
    val attachmentsResult = db.run(getEmailsAttachmentsQuery(emailsQuery.map(_._1)).result)
    val groupedAttachmentsResult =
      attachmentsResult
        .map(_
          .groupBy(_._1)
          .mapValues(_.map(_._2))
        )


    for {
      chatAddresses <- chatAddressesResult
      emails <- groupedEmails
      addresses <- emailAddressesPerEmail
      groupedAddresses <- emailAddressesPerEmailAndType
      overseesAddresses <- overseesAddressesResult
      userAddress <- userAddressResult
      attachments <- groupedAttachmentsResult
    } yield
      (
        chatAddresses,

        mergeEmailsAddressesAttachments(
          emails.filter{case (emailId, email) => filterAccessibleEmails(emailId, addresses.getOrElse(emailId, Seq()), overseesAddresses, userAddress)}
            .flatMap{ case (emailId, email) => email}.toSeq,
          groupedAddresses,
          attachments
        )
      )
  }

  private def getEmailsAndAddressesQuery(chatId: Int, userId: Int) = {
    val userAddressId = UsersTable.all.filter(_.userId === userId).map(_.addressId)
    val userAddress = AddressesTable.all.filter(_.addressId.in(userAddressId)).map(_.address)

    val emailsQuery = for {
      email <- EmailsTable.all.filter(_.chatId === chatId)
      // Filter the drafts: (the user can only see chat's drafts if the user is sender (from)
      fromAddressId <- EmailAddressesTable.all
        .filter(ea => ea.emailId === email.emailId && ea.participantType === "from")
        .map(_.addressId)
      fromAddress <- AddressesTable.all.filter(_.addressId === fromAddressId).map(_.address)
      if (email.sent === 0 && fromAddress.in(userAddress)) || email.sent === 1
    } yield (email.emailId, email.body, email.date, email.sent)

    val emailAddressesQuery = for {
      emailId <- emailsQuery.map(_._1)
      emailAddress <- EmailAddressesTable.all.filter(_.emailId === emailId)
      address <- AddressesTable.all.filter(_.addressId === emailAddress.addressId)
    } yield (emailAddress.emailId, emailAddress.participantType, address.address)

    (emailsQuery, emailAddressesQuery)
    // ( (emailId, body, date, sent), (emailId, participantType, address) )
  }

  private def getEmailsAttachmentsQuery(emailsIds: Query[Rep[Int], Int, scala.Seq]) = {
    AttachmentsTable.all
      .filter(_.emailId in emailsIds)
      .map(attachment => (attachment.emailId, attachment.attachmentId))
    // (emailId, attachmentId)
  }

  private def mergeEmailsAddressesAttachments(emails: Seq[(Int, String, String, Int)],
                                              addresses: Map[(Int, String), Seq[String]],
                                              attachments: Map[Int, Seq[Int]]
                                              ) = {

    emails.map{
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
          attachments.getOrElse(emailId, Seq())
        )
    }
  }



  private def filterAccessibleEmails(emailId: Int, addresses: Seq[String],
                             oversees: Seq[String], userAddress: String) = {
    oversees
      .foldLeft(false){
        case (acc, address) => acc || addresses.contains(address)
      } ||
      addresses.contains(userAddress)
  }


  /*** Overseers ***/

  private def processOverseers(chatId: Int) = {
    db.run(chatOverseersQuery(chatId).result)
      .map(_
          .groupBy(_._1) // group by user
          .mapValues(_.map(_._2))
          .toSeq
          .map(Overseer.tupled)
    )
  }

  private def chatOverseersQuery(chatId: Int) = {

    for {
      oversight <- OversightsTable.all.filter(_.chatId === chatId)

      overseeAddressId <- UsersTable.all.filter(_.userId === oversight.overseeId).map(_.addressId)
      overseeAddress <- AddressesTable.all.filter(_.addressId === overseeAddressId)
        .map(_.address)

      overseerAddressId <- UsersTable.all.filter(_.userId === oversight.overseerId).map(_.addressId)
      overseerAddress <- AddressesTable.all.filter(_.addressId === overseerAddressId)
        .map(_.address)
    } yield (overseeAddress, overseerAddress)
  }
}