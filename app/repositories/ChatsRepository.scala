package repositories


import repositories.dtos.{Address, ChatPreview}


import scala.concurrent.Future

trait ChatsRepository {
	
	def getChatPreview(mailbox: String, user: Int) : Future[Array[ChatPreview]]
	
	def getOverseeingChatPreview(user: Int) : Future[Array[ChatPreview]]

}
