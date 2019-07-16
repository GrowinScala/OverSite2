package services

import javax.inject.Inject
import repositories.slick.implementations.SlickChatsRepository

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

// Put the implementation here instead of the Trait because we're leaving injection for later
class ChatService @Inject() (chatsRep: SlickChatsRepository) {

}
