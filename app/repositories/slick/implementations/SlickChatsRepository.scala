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


  private def emailAddressesQuery(chatId: Int) =
    for {
      chatEmailId <- EmailsTable.all
        .filter(_.chatId === chatId).map(_.emailId)
      emailAddress: (Rep[Int], Rep[String]) <- EmailAddressesTable.all
        .filter(_.emailId === chatEmailId)
        .map(emailAddress => (emailAddress.addressId, emailAddress.receiverType))
      address: Rep[String] <- AddressesTable.all.filter(_.addressId === emailAddress._1).map(_.address)
    } yield (chatEmailId, emailAddress._2, address)

  private def receiversOfEmail(emailId: Rep[Int]) : Query[Rep[Int], Int, scala.Seq] = {
    // to, bcc, cc
    EmailAddressesTable.all.filter(_.emailId === emailId).map(_.addressId)
  }

  private def emailsQuery(chatId: Int, userId: Int) =
    for {
      userAddressId: Rep[Int] <- UsersTable.all.filter(_.userId === userId).map(_.addressId)
      email: EmailsTable <- EmailsTable.all
        .filter(email => email.chatId === chatId &&
          (email.fromAddressId === userAddressId ||             //user must be sender OR receiver of the email
            userAddressId.in(receiversOfEmail(email.emailId))   //in order to have permission to see it
          )
        )
      fromAddress: Rep[String] <- AddressesTable.all.filter(_.addressId === email.fromAddressId).map(_.address)

    } yield (email.emailId, fromAddress, email.body, email.date, email.sent)


  def chatEmailsQuery(chatId: Int, userId: Int) = {
    val query = for {
      (email, emailAddress) <- emailsQuery(chatId, userId).join(emailAddressesQuery(chatId)).on(_._1 === _._1)

    } yield (email._1, email._2, emailAddress._2, emailAddress._3, email._3, email._4, email._5)
    // (emailId, fromAddress, receiverType, receiverAddress, body, date, sent)

    db.run(query.result).map(_.map(Email.tupled))
  }

  def chatOverseersQuery(chatId: Int, userId: Int) = {

    val query = for {
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

    db.run(query.result).map(_.map(Overseer.tupled))
  }

  def chatInfoQuery(chatId: Int) = {
    val query = ChatsTable.all.filter(_.chatId === chatId).map(chat => (chat.chatId, chat.subject))

    db.run(query.result.headOption).map(_.map(Chat.tupled))
  }



}