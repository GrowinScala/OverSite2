package services


import javax.inject.Inject
import model.dtos.ChatPreviewDTO
import model.types.Mailbox
import model.types.Mailbox.Overseeing
import repositories.slick.implementations.SlickChatsRepository
import repositories.ChatsRepository

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


// Put the implementation here instead of the Trait because we're leaving injection for later
class ChatService @Inject() (chatsRep: SlickChatsRepository) {
	
	def getChats(mailbox: Mailbox, user: Int) : Future[Array[ChatPreviewDTO]] =
		if(mailbox == Overseeing)
			chatsRep.getOverseeingChatPreview(user)
		chatsRep.getChatPreview(mailbox, user).map(_.map(chatPreview =>
			ChatPreviewDTO(chatPreview.chatId, chatPreview.subject, chatPreview.lastAddress, chatPreview.lastEmailDate,
				chatPreview.contentPreview)))



}
