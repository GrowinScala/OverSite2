
package repositories.slick.implementations

import javax.inject.Inject
import model.types.Mailbox
import model.types.Mailbox._
import repositories.ChatsRepository
import repositories.dtos.ChatPreview
import repositories.slick.mappings._
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }

class SlickChatsRepository @Inject() (db: Database)(implicit executionContext: ExecutionContext)
  extends ChatsRepository {

  val PREVIEW_BODY_LENGTH: Int = 30
/******* Queries here **********/

  /*	def test : Future[Array[Int]]  = {
		val user = 3
		val mailbox = "overseeing"

		def getUserChatPreview (mailbox: String, user: Int) = {

			val mailboxChatsIdQuery =
				if (mailbox == "overseeing")
					for {
						userChatId <- OversightsTable.all.filter(_.userId === user).map(_.userChatId)
						chatId <- UserChatsTable.all.filter(_.userChatId === userChatId).map(_.chatId)

					}yield chatId

				else UserChatsTable.all.filter(
					userChatRow => userChatRow.userId === user && (userChatRow.mailBox like s"%$mailbox%")).map(_.chatId)


			val userAddressIdQuery = UsersTable.all.filter(_.userId === user).map(_.addressId)

			val onlyVisibleEmailsQuery = {

				val receivedEmailsQuery = for {
					userAddressId <- userAddressIdQuery
					mailboxChats <- mailboxChatsIdQuery
					chatEmailsIds <- EmailsTable.all.filter(_.chatId === mailboxChats).map(_.emailId)
					receivedEmails <- EmailAddressesTable.all.filter(
						emailAddressRow => emailAddressRow.emailId === chatEmailsIds &&
							emailAddressRow.addressId === userAddressId).map(_.emailId)
				} yield receivedEmails

				val sentEmails = for {
					userAddressId <- userAddressIdQuery
					sentEmails <- EmailsTable.all.filter(_.fromAddressId === userAddressId).map(_.emailId)
				} yield sentEmails

				receivedEmailsQuery.union(sentEmails)

			}

			val chatPreviewBaseQuery = for {
				mailboxChats <- mailboxChatsIdQuery
				(chatId, subject) <- ChatsTable.all.filter(_.chatId === mailboxChats)
					.map(chatsTable => (chatsTable.chatId, chatsTable.subject))
				onlyVisibleEmails <- onlyVisibleEmailsQuery

				(date, bodyPreview, fromAddressId) <- EmailsTable.all.filter(emailsRow =>
					emailsRow.emailId === onlyVisibleEmails && emailsRow.chatId === chatId).map(
					emailsTable => (emailsTable.date, emailsTable.body.substring(0, 10000), emailsTable.fromAddressId))

				address <- AddressesTable.all.filter(_.addressId === fromAddressId).map(_.address)

			} yield (chatId, subject, address, date, bodyPreview)

			val recentDateQuery = chatPreviewBaseQuery.groupBy(_._1).map {
				case (chatIdK, agg) => (chatIdK, agg.map(_._4).max)
			}

			val chatPreviewQuery = chatPreviewBaseQuery.join(recentDateQuery).on { case (baseQuery, dateQuery) =>
				baseQuery._1 === dateQuery._1 && baseQuery._4 === dateQuery._2
			}.map(_._1).sortBy(_._1.desc)

			chatPreviewQuery
		}

		val getChatPreview = {
		for {
					overseeChatId <- OversightsTable.all.filter(_.userId === user).map(_.userChatId)
					overseeId <- UserChatsTable.all.filter(_.userChatId === overseeChatId).map(_.userId)
				}yield overseeId

		}

		val result = db.run(getChatPreview.result)

		result.map(_.toArray)

		}*/

  def getChatsPreview(mailbox: Mailbox, userId: Int): Future[Seq[ChatPreview]] = {
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

    val overseesEmailIdQuery = for {
      (oversightChatId, overseeId) <- OversightsTable.all.filter(_.overseerId === userId)
        .map(oversightRow => (oversightRow.chatId, oversightRow.overseeId))
      overseeAddressId <- UsersTable.all.filter(_.userId === overseeId).map(_.addressId)
      emailId <- EmailAddressesTable.all.filter(_.addressId === overseeAddressId).map(_.emailId)
      _ <- EmailsTable.all.filter(emailRow => emailRow.emailId === emailId && emailRow.chatId === oversightChatId)
    } yield emailId

    val filteredQuery = for {
      userAddressId <- UsersTable.all.filter(_.userId === userId).map(_.addressId)

      (chatId, date) <- baseQuery.filter {
        case (chatId, emailId, date, sent, addressId, participantType) =>
          (addressId === userAddressId || emailId.in(overseesEmailIdQuery)) &&
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
