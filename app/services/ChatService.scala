package services


import javax.inject.Inject
import model.dtos.{ChatDTO, EmailDTO, OverseersDTO}
import repositories.dtos.{Email, Overseer}
import repositories.slick.implementations.SlickChatsRepository

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


// Put the implementation here instead of the Trait because we're leaving injection for later
class ChatService @Inject() (chatsRep: SlickChatsRepository) {

  def getChat(chatId: Int, userId: Int)/*: Future[ChatDTO]*/ = {

    val emailsQuery =
      chatsRep
        .chatEmailsQuery(chatId, userId)
        .map(_
          .groupBy(_.emailId)       // group by emailId
          .toSeq                    // because groupBy returns a Map and Maps are unordered
          .sortBy(_._2.head.date)   // order by date
          .map{
            case (id, emails) => toEmailDTO(id, emails)
          }
          .toArray
        )

    val overseersQuery =
      chatsRep
        .chatOverseersQuery(chatId, userId)
        .map(_
          .groupBy(_.user)
          .map{
            case (user, overseers) => toOverseersDTO(user, overseers)
          }
          .toArray
        )

    val chatQuery =
      for {
        chat <- chatsRep.chatInfoQuery(chatId)
        overseers <- overseersQuery
        emails <- emailsQuery
      } yield ChatDTO(chat.get.chatId, chat.get.subject, Array(), overseers, emails)

    chatQuery
  }

  def toOverseersDTO(user: String, overseers: Seq[Overseer]): OverseersDTO = {
    val overseersAddresses = overseers.map(_.overseer).toArray
    OverseersDTO(user, overseersAddresses)
  }

  /**
    * @param emailId Email ID
    * @param emails Email sequence with the same emailId, from, body, date and sent
    *               but differ in the receiverType and the receiverAddress
    * @return EmailDTO of the email with all the receiver types (to, bcc and cc) aggregated
    */
  def toEmailDTO (emailId: Int, emails: Seq[Email]): EmailDTO = {
    val email = emails.head   // except for the receiverType and the addressType, all the emails are the same

    val addresses =
      emails
      .map(email => (email.receiverType, email.receiverAddress))
      .groupBy(_._1)  // group by receiverType (to, bcc or cc)
      .map { case (receiverType, groupedAddresses) =>
        val addresses = groupedAddresses.map(_._2).toArray
        (receiverType, addresses)
      }

    val toAddresses = addresses.getOrElse("to", Array())
    val bccAddresses = addresses.getOrElse("bcc", Array())
    val ccAddresses = addresses.getOrElse("cc", Array())

    EmailDTO(
      email.emailId,
      email.from,
      toAddresses,
      bccAddresses,
      ccAddresses,
      email.body,
      email.date,
      intToBoolean(email.sent)
    )
  }

  def intToBoolean(i: Int): Boolean = if (i != 0) true else false

  /*
  def getChat(chatId: Int, userId: Int)/*: Future[ChatDTO]*/ = {

    val emails =
      chatsRep
        .chatEmailsQuery(chatId)
        .map(_
          .groupBy(_._1) //group by emailId
          .map{ rowGroupedByEmailId =>
            val email = rowGroupedByEmailId._2.head
            val emailId = email._1
            val from = email._2
            val body = email._5
            val date = email._6
            val sent = if (email._7 == 1) true else false

            val addresses = rowGroupedByEmailId._2
              .groupBy(_._3)  // group by receiverType (to, bcc or cc)
              .map { rowGroupedByReceiverType =>
                val addresses = rowGroupedByReceiverType._2.map(_._4).toArray
                val receiverType = rowGroupedByReceiverType._1
                (receiverType, addresses)
              }

            val toAddresses = addresses.getOrElse("to", Array())
            val bccAddresses = addresses.getOrElse("bcc", Array())
            val ccAddresses = addresses.getOrElse("cc", Array())

            EmailDTO(emailId, from, toAddresses, bccAddresses, ccAddresses, body, date, sent) // return EmailsDTO here
          })


    emails
  }*/

}

/*
val overseersDTO : Future[Array[OverseersDTO]] =
  chatsRep
    .chatOverseersQuery(chatId, userId)
    .map(_.groupBy(_._1)
          .mapValues(_.map{ case (user, overseer) => overseer}.toArray)
          .toSeq.map(OverseersDTO.tupled)
          .toArray
    )


for {
  (chatId, subject) <- chatsRep.getChat(chatId)
  emails <- emailsDTOs
  overseers <- overseersDTO
} yield ChatDTO(chatId, subject, Array(""), overseers, emails)

chatsRep.getChat(chatId).map{ case (id, subject) => ChatDTO(id, subject, Array(), Array(), Array()) }
*/
