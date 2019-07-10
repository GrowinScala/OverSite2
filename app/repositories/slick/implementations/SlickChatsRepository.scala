package repositories.slick.implementations

import javax.inject.Inject
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.db.NamedDatabase
import repositories.ChatsRepository
import repositories.dtos.{Chat, Email, Overseer}
import repositories.slick.mappings._
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}



class SlickChatsRepository @Inject()
(@NamedDatabase("oversitedb") protected val dbConfigProvider: DatabaseConfigProvider)
(implicit executionContext: ExecutionContext)
	extends ChatsRepository with HasDatabaseConfigProvider[JdbcProfile] {


  /*** Chat ***/
  private def processChat(chatId: Int) = {
    val query = ChatsTable.all.filter(_.chatId === chatId).map(_.subject)
    db.run(query.result.head)     // TODO headOption (chat might not exist)
  }

  def getChat(chatId: Int, userId: Int) = {
    val userAddressQuery = for {
      userAddressId <- UsersTable.getUserAddressId(userId)
      userAddress <- AddressesTable.all.filter(_.addressId === userAddressId).map(_.address)
    } yield userAddress

    val userAddress: Future[String] = db.run(userAddressQuery.result.head)  // TODO headOption (user might not exist)

    for {
      subject <- processChat(chatId)
      (addresses, emails) <- processEmailsAndAddresses(chatId)
      overseers <- processOverseers(chatId, userId)
    } yield
      Chat(
        chatId,
        subject,
        addresses,
        overseers,
        emails
      )

  }

  /*** Email DTO ***/
  private def allChatEmailsQuery(chatId: Int) = {
    //Get all the emails of the chat
    for {
      email <- EmailsTable.getChatEmails(chatId)
    } yield (email.emailId, email.chatId, email.body, email.date, email.sent)
    // (emailId, chatId, body, date, sent)
  }

  private def allChatEmailsAddressesQuery(emailsIds: Query[Rep[Int], Int, scala.Seq]) = {
    //Get all the email_addresses for those email IDs and JOIN with the table Addresses
    for {
      (emailAddress, address) <- EmailAddressesTable.all.filter(_.emailId in emailsIds)
        .join(AddressesTable.all).on(_.addressId === _.addressId)
    } yield (emailAddress.emailId, emailAddress.receiverType, address.address)
    // (emailId, receiverType, address)
  }

  private def allChatEmailsAttachmentsQuery(emailsIds: Query[Rep[Int], Int, scala.Seq]) = {
    AttachmentsTable.all
      .filter(_.emailId in emailsIds)
      .map(attachment => (attachment.emailId, attachment.attachmentId))
    // (emailId, attachmentId)
  }

  private def mergeEmailsAddressesAttachments(emails: Seq[(Int, Int, String, String, Int)],
                                      addresses: Map[(Int, String), Seq[String]],
                                      attachments: Map[Int, Seq[Int]]
                             ) = {

    emails.map{
      case (emailId, chatId, body, date, sent) =>
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

  private def processEmailsAndAddresses(chatId: Int) : Future[(Seq[String], Seq[Email])] = {
    val emailsQuery = allChatEmailsQuery(chatId)

    val emailsResult = db.run(emailsQuery.result)

    val addressesResult = db.run(allChatEmailsAddressesQuery(emailsQuery.map(_._1)).result)

    val groupedAddressesResult =
      addressesResult
        .map(_
          .groupBy(emailAddress => (emailAddress._1, emailAddress._2))  //group by email ID and receiver type (from, to, bcc, cc)
          .mapValues(_.map(_._3))   // Map: (emailId, receiverType) -> addresses
        )

    val attachmentsResult = db.run(allChatEmailsAttachmentsQuery(emailsQuery.map(_._1)).result)

    val groupedAttachmentsResult =
      attachmentsResult
        .map(_
          .groupBy(_._1)
          .mapValues(_.map(_._2))
        )

    // All addresses that sent and received emails in this chat
    val chatAddressesResult = addressesResult.map(_.map(_._3).distinct)

    for {
      chatAddresses <- chatAddressesResult
      emails <- emailsResult
      addresses <- groupedAddressesResult
      attachments <- groupedAttachmentsResult
    } yield (chatAddresses, mergeEmailsAddressesAttachments(emails, addresses, attachments))

  }

  /*** Overseers DTO ***/
  private def chatOverseersQuery(chatId: Int, userId: Int) = {

    for {
      /** Overseer **/
      (userChatId, overseenId) <- UserChatsTable.all.filter(_.chatId === chatId).map( uc => (uc.userChatId, uc.userId) )
      overseerId <- OversightsTable.all.filter(_.userChatId === userChatId).map(_.userId)
      overseerAddressId <- UsersTable.all.filter(_.userId === overseerId).map(_.addressId)
      overseerAddress <- AddressesTable.all.filter(_.addressId === overseerAddressId).map(_.address)

      /** Overseen **/
      // User whose chat is being overseen
      userAddressId <- UsersTable.all.filter(_.userId === overseenId).map(_.addressId)
      userAddress <- AddressesTable.all.filter(_.addressId === userAddressId).map(_.address)

    } yield (userAddress, overseerAddress)
  }

  private def processOverseers(chatId: Int, userId: Int) = {
    db.run(chatOverseersQuery(chatId, userId).result)
      .map(_
          .groupBy(_._1) // group by user
          .mapValues(_.map(_._2))
          .toSeq
          .map(Overseer.tupled)
    )
  }
}