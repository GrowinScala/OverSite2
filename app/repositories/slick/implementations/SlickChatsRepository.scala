
package repositories.slick.implementations

import javax.inject.Inject
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.db.NamedDatabase
import repositories.ChatsRepository
import repositories.dtos.ChatPreview
import repositories.slick.mappings._
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}


class SlickChatsRepository @Inject()
(@NamedDatabase("oversitedb") protected val dbConfigProvider: DatabaseConfigProvider)
(implicit executionContext: ExecutionContext)
	extends ChatsRepository with HasDatabaseConfigProvider[JdbcProfile] {
	
	/******* Queries here **********/
	
	
	def test : Future[Array[Int]]  = {
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
		
		}
	
	
	def getChatPreview(mailbox: String, user: Int) : Future[Array[ChatPreview]] ={
		
		
		def getUserChatPreview (mailbox: String, user: Int, userChatId: Int) = {
			
			val mailboxChatsIdQuery =
				if (mailbox == "overseeing")
				UserChatsTable.all.filter(_.userChatId === userChatId).map(_.chatId)
				
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
			if (mailbox == "overseeing") {
				val overseesQuery = for {
					overseesChatIds <- OversightsTable.all.filter(_.userId === user).map(_.userChatId)
					oversees <- UserChatsTable.all.filter(_.userChatId === overseesChatIds)
						.map(userChatRow => (userChatRow.userId, userChatRow.userChatId))
				}yield oversees
				
				val oversees = db.run(overseesQuery.result)
				oversees.map(_.map(
					oversee => getUserChatPreview(mailbox, oversee._1 , oversee._2)).reduce(_.union(_)))
			}
			
			else Future(getUserChatPreview(mailbox,user, 0))
		}
		
		
		val result = getChatPreview.flatMap(query => db.run(query.result))
		
		result.map(_.map(ChatPreview.tupled).toArray)
	}
	
	
	def getOverseeingChatPreview(user: Int) : Future[Array[ChatPreview]] = ???
	
	
	def getChatPreviewNOTOVER(mailbox: String, user: Int) : Future[Array[ChatPreview]] ={
		
		
		val mailboxChatsIdQuery = UserChatsTable.all.filter(
			userChatRow => userChatRow.userId === user && (userChatRow.mailBox like s"%$mailbox%")).map(_.chatId)
		
		val userAddressIdQuery = UsersTable.all.filter(_.userId === user).map(_.addressId)
		
		val onlyVisibleEmailsQuery = {
			
			val receivedEmailsQuery = for {
				userAddressId  <- userAddressIdQuery
				mailboxChats   <- mailboxChatsIdQuery
				chatEmailsIds  <- EmailsTable.all.filter(_.chatId === mailboxChats).map(_.emailId)
				receivedEmails <- EmailAddressesTable.all.filter(
					emailAddressRow => emailAddressRow.emailId === chatEmailsIds &&
						emailAddressRow.addressId === userAddressId).map(_.emailId)
			}yield receivedEmails
			
			val sentEmails = for {
				userAddressId  <- userAddressIdQuery
				sentEmails     <- EmailsTable.all.filter(_.fromAddressId === userAddressId).map(_.emailId)
			}yield sentEmails
			
			receivedEmailsQuery.union(sentEmails)
			
		}
		
		val chatPreviewBaseQuery = for {
			mailboxChats                       <- mailboxChatsIdQuery
			(chatId, subject)                  <- ChatsTable.all.filter(_.chatId === mailboxChats)
				.map(chatsTable => (chatsTable.chatId, chatsTable.subject))
			onlyVisibleEmails                  <- onlyVisibleEmailsQuery
			
			(date, bodyPreview, fromAddressId) <- EmailsTable.all.filter( emailsRow =>
				emailsRow.emailId === onlyVisibleEmails && emailsRow.chatId === chatId).map(
				emailsTable => (emailsTable.date, emailsTable.body.substring(0,10000), emailsTable.fromAddressId))
			
			address <- AddressesTable.all.filter(_.addressId === fromAddressId).map(_.address)
			
		}yield (chatId, subject, address, date, bodyPreview)
		
		val recentDateQuery = chatPreviewBaseQuery.groupBy(_._1).map{
			case (chatIdK, agg) => (chatIdK, agg.map(_._4).max)
		}
		
		val chatPreviewQuery = chatPreviewBaseQuery.join(recentDateQuery).on{case (baseQuery, dateQuery) =>
			baseQuery._1 === dateQuery._1 && baseQuery._4 === dateQuery._2}.map(_._1).sortBy(_._1.desc)
		
		val result = db.run(chatPreviewQuery.result)
		
		result.map(_.map(ChatPreview.tupled).toArray)
	}
	
	
	/*def getChatPreviewNEW(mailbox: String, user: Int) : Future[Array[ChatPreview]] ={
		
		
		val mailboxChatsIdQuery = UserChatsTable.all.filter(
			userChatRow => userChatRow.userId === user && (userChatRow.mailBox like s"%$mailbox%")).map(_.chatId)
		
		val userAddressIdQuery = UsersTable.all.filter(_.userId === user).map(_.addressId)
		
		val onlyVisibleEmailsQuery = {
			
			val receivedEmailsQuery = for {
			userAddressId  <- userAddressIdQuery
			mailboxChats   <- mailboxChatsIdQuery
			chatEmailsIds  <- EmailsTable.all.filter(_.chatId === mailboxChats).map(_.emailId)
			receivedEmails <- EmailAddressesTable.all.filter(
												emailAddressRow => emailAddressRow.emailId === chatEmailsIds &&
												emailAddressRow.addressId === userAddressId).map(_.emailId)
			}yield receivedEmails
			
			val sentEmails = for {
				userAddressId  <- userAddressIdQuery
				sentEmails     <- EmailsTable.all.filter(_.fromAddressId === userAddressId).map(_.emailId)
			}yield sentEmails
			
			receivedEmailsQuery.union(sentEmails)
		
		}
		
		val chatPreviewBaseQuery = for {
			mailboxChats                       <- mailboxChatsIdQuery
			(chatId, subject)                  <- ChatsTable.all.filter(_.chatId === mailboxChats)
																				 	 .map(chatsTable => (chatsTable.chatId, chatsTable.subject))
			onlyVisibleEmails                  <- onlyVisibleEmailsQuery
			
			(date, bodyPreview, fromAddressId) <- EmailsTable.all.filter(_.emailId === onlyVisibleEmails).map(
				emailsTable => (emailsTable.date, emailsTable.body.substring(0,10000), emailsTable.fromAddressId))
			
			address <- AddressesTable.all.filter(_.addressId === fromAddressId).map(_.address)
			
		}yield (chatId, subject, address, date, bodyPreview)
		
		val recentDateQuery = chatPreviewBaseQuery.groupBy(_._1).map{
			case (chatIdK, agg) => (chatIdK, agg.map(_._4).max)
		}
		
		val chatPreviewQuery = chatPreviewBaseQuery.join(recentDateQuery).on{case (baseQuery, dateQuery) =>
			baseQuery._1 === dateQuery._1 && baseQuery._4 === dateQuery._2}.map(_._1).sortBy(_._1.desc)
		
		val result = db.run(chatPreviewQuery.result)
		
		result.map(_.map(ChatPreview.tupled).toArray)
	}*/
	

	/*def getChatPreviewOLD(mailbox: String, user: Int) : Future[Array[ChatPreview]] ={
		
		val query = for {
			(chatId, subject) <- ChatsTable.all.map(chatsTable => (chatsTable.chatId, chatsTable.subject))
			
			(date, bodyPreview, fromAddressId) <- EmailsTable.all.filter(_.chatId === chatId).map(
				emailsTable => (emailsTable.date, emailsTable.body.substring(0,10), emailsTable.fromAddressId))
			
			address <- AddressesTable.all.filter(_.addressId === fromAddressId).map(_.address)
			
		}yield (chatId, subject, address, date, bodyPreview)
		
		val recentDateQuery = query.groupBy(_._1).map{
				case (chatIdK, agg) =>
					(chatIdK, agg.map(_._4).max)
		}
		
		val joined = query.join(recentDateQuery).on{case (baseQuery, dateQuery) =>
			baseQuery._1 === dateQuery._1 && baseQuery._4 === dateQuery._2}.map(_._1).sortBy(_._1.desc)
		
		val result = db.run(joined.result)
		
		result.map(_.map(ChatPreview.tupled).toArray)
	}*/
	
	
	}
	