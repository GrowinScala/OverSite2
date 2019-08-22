package repositories.slick.implementations

import javax.inject.Inject
import model.dtos.{ CreateChatDTO, UpsertEmailDTO }
import model.types.Mailbox
import model.types.Mailbox._
import repositories.ChatsRepository
import repositories.slick.mappings._
import repositories.dtos._

import slick.dbio.DBIOAction
import slick.jdbc.MySQLProfile.api._
import utils.DateUtils
import utils.Generators._

import scala.concurrent.{ ExecutionContext, Future }

class SlickChatsRepository @Inject() (db: Database)(implicit executionContext: ExecutionContext)
  extends ChatsRepository {

  val PREVIEW_BODY_LENGTH: Int = 30

  //region Shared auxiliary methods
  /**
   * This query returns, for a user, all of its chats and for EACH chat, all of its emails
   * (without body) and for each email, all of its participants
   * @param userId Id of the user
   * @param optBox Optional Mailbox specification. If used, a chat will only be shown if the user has it inside
   *               the specified mailbox.
   * @return Query: For a user, all of its chats and for EACH chat, all of its emails
   * (without body) and for each email, all of its participants
   */
  private[implementations] def getChatsMetadataQueryByUserId(userId: String, optBox: Option[Mailbox] = None) = {
    for {
      chatId <- UserChatsTable.all.filter(userChatRow =>
        userChatRow.userId === userId &&
          (optBox match {
            case Some(Inbox) => userChatRow.inbox === 1
            case Some(Sent) => userChatRow.sent === 1
            case Some(Trash) => userChatRow.trash === 1
            case Some(Drafts) => userChatRow.draft === 1
            case None => true
          })).map(_.chatId)

      (emailId, body, date, sent) <- EmailsTable.all.filter(_.chatId === chatId).map(
        emailRow => (emailRow.emailId, emailRow.body, emailRow.date, emailRow.sent))

      (addressId, participantType) <- EmailAddressesTable.all.filter(_.emailId === emailId)
        .map(emailAddressRow =>
          (emailAddressRow.addressId, emailAddressRow.participantType))

    } yield (chatId, emailId, body, date, sent, addressId, participantType)
  }

  /**
   * Method that returns all the addressIds of the oversees of a given overseer for a given chat
   * @param userId The userId of the overseer
   * @param chatId The chat in question
   * @return Query addressIds of the oversees for the given chat
   */
  private def getUserChatOverseesQuery(userId: String, chatId: Rep[String]) = {
    OversightsTable.all
      .join(UsersTable.all)
      .on((oversightRow, userRow) =>
        userRow.userId === oversightRow.overseeId &&
          oversightRow.overseerId === userId &&
          oversightRow.chatId === chatId)
      .map { case (oversight, oversee) => oversee.addressId }
  }

  /**
   * Method that retrieves the emails that a specific user can see:
   * @param userId The user in question
   * @param optBox Optional filter for a given mailbox
   * @return The emails that a specific user can see:
   * - If the user is a participant of the email (from, to, bcc, cc)
   *   OR if the user is overseeing another user in the chat (has access to the same emails the oversee has,
   *   excluding the oversee's drafts)
   * - AND if email is draft (sent = 0), only the user with the "from" address can see it
   */
  private def getVisibleEmailsQuery(userId: String, optBox: Option[Mailbox] = None) =
    for {
      userAddressId <- UsersTable.all.filter(_.userId === userId).map(_.addressId)

      (chatId, emailId, body, date, sent) <- getChatsMetadataQueryByUserId(userId, optBox).filter {
        case (chatId, emailId, body, date, sent, addressId, participantType) =>
          (addressId === userAddressId || addressId.in(getUserChatOverseesQuery(userId, chatId))) &&
            (sent === 1 || (participantType === "from" && addressId === userAddressId))
      }.map(filteredRow => (filteredRow._1, filteredRow._2, filteredRow._3, filteredRow._4, filteredRow._5))

    } yield (chatId, emailId, body, date, sent)
  //endregion

  /**
   * Method that returns a preview of all the chats of a given user in a given Mailbox
   * @param mailbox The mailbox being seen
   * @param userId The userId of the user in question
   * @return A Future sequence of ChatPreview dtos. The preview of each chat only shows the most recent email
   */
  def getChatsPreview(mailbox: Mailbox, userId: String): Future[Seq[ChatPreview]] = {

    val groupedQuery = getVisibleEmailsQuery(userId, Some(mailbox))
      .map(visibleEmailRow => (visibleEmailRow._1, visibleEmailRow._4))
      .groupBy(_._1)
      .map { case (chatId, date) => (chatId, date.map(_._2).max) }

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

    val resultOption = db.run(chatPreviewQuery.sortBy(_._4.desc).result)

    val result = resultOption.map(_.map {
      case (chatId, subject, address, dateOption, body) =>
        (chatId, subject, address, dateOption.getOrElse("Missing Date"), body)
    })

    result.map(_.map(ChatPreview.tupled))

  }

  //region getChat and auxiliary methods
  /**
   * Method to get the emails and other data of a specific chat of a user
   * @param chatId ID of the chat requested
   * @param userId ID of the user who requested the chat
   * @return a Chat DTO that carries
   *         the chat's subject,
   *         the addresses involved in the chat,
   *         the overseers of the chat
   *         and the emails of the chat (that the user can see)
   */
  def getChat(chatId: String, userId: String): Future[Option[Chat]] = {

    for {
      chatData <- getChatData(chatId, userId)
      (addresses, emails) <- getGroupedEmailsAndAddresses(chatId, userId, None)
      overseers <- getOverseersData(chatId)
    } yield chatData.map {
      case (id, subject, _) =>
        Chat(
          id,
          subject,
          addresses,
          overseers,
          emails)
    }

  }

  def postChat(createChatDTO: CreateChatDTO, userId: String): Future[CreateChatDTO] = {
    val emailDTO: UpsertEmailDTO = createChatDTO.email
    val date = DateUtils.getCurrentDate

    /** Generate chatId, userChatId and emailId **/
    val chatId = genUUID
    val userChatId = genUUID
    val emailId = genUUID

    val inserts = for {
      _ <- ChatsTable.all += ChatRow(chatId, createChatDTO.subject.getOrElse(""))
      _ <- UserChatsTable.all += UserChatRow(userChatId, userId, chatId, 0, 0, 1, 0)

      // This assumes that the authentication guarantees that the user exists and has a correct address
      fromAddress <- UsersTable.all.join(AddressesTable.all)
        .on { case (user, address) => user.addressId === address.addressId && user.userId === userId }
        .map(_._2.address).result.head

      _ <- upsertEmail(emailDTO, chatId, emailId, Some(fromAddress), date)

    } yield fromAddress

    db.run(inserts.transactionally).map(fromAddress =>
      createChatDTO
        .copy(
          chatId = Some(chatId),
          email = emailDTO.copy(emailId = Some(emailId), from = Some(fromAddress), date = Some(date))))
  }

  def postEmail(createEmailDTO: UpsertEmailDTO, chatId: String, userId: String): Future[Option[CreateChatDTO]] = {
    val date = DateUtils.getCurrentDate

    val emailId = genUUID

    val insertAndUpdate = for {
      chatAddress <- getChatDataAddressQuery(chatId, userId).result.headOption

      _ <- chatAddress match {
        case Some((chatID, subject, fromAddress)) => upsertEmail(createEmailDTO, chatId, emailId, Some(fromAddress), date)
          .andThen(UserChatsTable.updateDraftField(userId, chatId, 1))
        case None => DBIOAction.successful(None)
      }

    } yield chatAddress

    db.run(insertAndUpdate.transactionally).map {
      case Some(tuple) => tuple match {
        case (chatID, subject, fromAddress) => Some(CreateChatDTO(
          Some(chatID),
          Some(subject),
          UpsertEmailDTO(
            emailId = Some(emailId),
            from = Some(fromAddress),
            to = createEmailDTO.to,
            bcc = createEmailDTO.bcc,
            cc = createEmailDTO.cc,
            body = createEmailDTO.body,
            date = Some(date),
            sent = Some(false))))
      }
      case None => None
    }

  }

  def patchEmail(upsertEmailDTO: UpsertEmailDTO, chatId: String, emailId: String, userId: String): Future[Option[Email]] = {

    val sent: Boolean = upsertEmailDTO.sent.getOrElse(false)

    val receiversAddresses: Set[String] =
      upsertEmailDTO.to.getOrElse(Set()) ++ upsertEmailDTO.bcc.getOrElse(Set()) ++ upsertEmailDTO.cc.getOrElse(Set())

    val senderUserChatQuery = UserChatsTable.all.filter(uc => uc.chatId === chatId && uc.userId === userId && uc.draft === 1)

    //Query
    val getFromAddress = for {
      //user needs to already have a userChat for that chat and have "draft permissions" for it
      userChat <- senderUserChatQuery
      fromAddress <- UsersTable.all.join(AddressesTable.all)
        .on((user, address) => user.userId === userChat.userId && user.addressId === address.addressId)
        .map(_._2.address)
    } yield fromAddress

    //TODO get number of drafts the user has on this chat

    //Action
    val updateEmail = for {
      fromAddressOption <- getFromAddress.result.headOption.map {
        case Some(fromAddress) => upsertEmail(upsertEmailDTO, chatId, emailId, Some(fromAddress), DateUtils.getCurrentDate)
        case None => DBIO.successful(0) //.failed(new Exception("This user cannot update this email")) //TODO Custom Exception ?
      }
      //Updates EmailsTable, adds addresses and email addresses if they do not exist
    } yield fromAddressOption

    val sendEmail = if (sent) {
      val receiverUserIdsOption = receiversAddresses.toSeq.map {
        receiverAddress =>
          AddressesTable.all.join(UsersTable.all)
            .on((address, user) => address.address === receiverAddress && address.addressId === user.addressId)
            .map(_._2.userId).result.headOption
      }

      val sendEmailAction = for {
        receiverUserIds <- DBIO.sequence(receiverUserIdsOption)

        rowAction <- DBIO.sequence(
          receiverUserIds.flatten //to get rid of the Nones (addresses that did not have a user)
            .map(receiverUserId =>
              // get the userChat of the user that will receive the email (they might not have a userChat yet)
              (UserChatsTable.all.filter(uc => uc.chatId === chatId && uc.userId === receiverUserId).result.headOption, receiverUserId))
            .map {
              case (userChatRowAction, receiverUserId) => userChatRowAction.flatMap {
                case Some(userChatRow) => DBIO.successful(userChatRow.copy(inbox = 1)) //if the user was already part of the chat
                case None => DBIO.successful(UserChatRow(genUUID, receiverUserId, chatId, 1, 0, 0, 0)) //if the user did not have the chat yet
              }
            }
            .map(_.map(row => UserChatsTable.all.insertOrUpdate(row))))
        //INSERT the row if the user did not have the chat yet. UPDATE the row if the user was already part of the chat

        _ <- senderUserChatQuery.map(uc => (uc.sent, uc.draft)).update(1, 0)

      } yield rowAction

      Some(sendEmailAction)

    } else None

    for {
      _ <- db.run(DBIO.seq(updateEmail, sendEmail.getOrElse(DBIO.successful(0))).transactionally)
      groupedEmailsAndAddresses <- getGroupedEmailsAndAddresses(chatId, userId, Some(emailId))
    } yield groupedEmailsAndAddresses._2.headOption

  }

  def moveChatToTrash(chatId: String, userId: String): Future[Boolean] = {
    db.run(UserChatsTable.moveChatToTrash(userId, chatId))
      .map(_ != 0)
  }

  private def getEmailAddressByEmailIdAndParticipantTypeQuery(emailId: String, participantType: String): Query[AddressesTable, AddressRow, scala.Seq] = {
    EmailAddressesTable.all.join(AddressesTable.all)
      .on((emailAddressRow, addressRow) =>
        emailAddressRow.emailId === emailId &&
          emailAddressRow.participantType === participantType &&
          emailAddressRow.addressId === addressRow.addressId)
      .map(_._2)
  }
  /*
  private def insertUpdateOrDeleteEmailAddresses(upsertEmailDTO: UpsertEmailDTO, chatId: String,
    emailId: String, optionFromAddress: Option[String]) = {
    for {
      existingToAddresses <- getEmailAddressByEmailIdAndParticipantTypeQuery(emailId, "to").result
      existingBccAddresses <- getEmailAddressByEmailIdAndParticipantTypeQuery(emailId, "bcc").result
      existingCcAddresses <- getEmailAddressByEmailIdAndParticipantTypeQuery(emailId, "cc").result

      newToAddresses = upsertEmailDTO.to.getOrElse(Set())
      newBccAddresses = upsertEmailDTO.bcc.getOrElse(Set())
      newCcAddresses = upsertEmailDTO.cc.getOrElse(Set())

      toAddressesToDelete = existingToAddresses.toSet -- newToAddresses
      bccAddressesToDelete = existingBccAddresses.toSet -- newBccAddresses
      ccAddressesToDelete = existingCcAddresses.toSet -- newCcAddresses

      toAddressesToAdd = newToAddresses -- existingToAddresses.toSet
      bccAddressesToAdd = newBccAddresses -- existingBccAddresses.toSet
      ccAddressesToAdd = newCcAddresses -- existingCcAddresses.toSet

      x <- toAddressesToDelete.map(address =>
        EmailAddressesTable.all.filter(emailAddressRow =>
          emailAddressRow.emailId === emailId &&))
    } yield ()

  }*/

  private[implementations] def upsertEmail(upsertEmailDTO: UpsertEmailDTO, chatId: String,
    emailId: String, optionFromAddress: Option[String], date: String) = {

    val sent: Int = if (upsertEmailDTO.sent.getOrElse(false)) 1 else 0

    for {
      emailInsert <- EmailsTable.all.insertOrUpdate(EmailRow(emailId, chatId, upsertEmailDTO.body.getOrElse(""), date, sent))

      fromInsert = optionFromAddress match {
        case Some(fromAddress) => upsertEmailAddress(emailId, chatId, upsertAddress(fromAddress), "from")
        case None => DBIO.successful(0)
      }
      //fromInsert = upsertEmailAddress(emailId, chatId, upsertAddress(fromAddress), "from")
      toInsert = upsertEmailDTO.to.getOrElse(Set()).map(
        to => upsertEmailAddress(emailId, chatId, upsertAddress(to), "to"))
      bccInsert = upsertEmailDTO.bcc.getOrElse(Set()).map(
        bcc => upsertEmailAddress(emailId, chatId, upsertAddress(bcc), "bcc"))
      ccInsert = upsertEmailDTO.cc.getOrElse(Set()).map(
        cc => upsertEmailAddress(emailId, chatId, upsertAddress(cc), "cc"))

      numberOfRowsUpdated <- DBIO.sequence(Vector(fromInsert) ++ toInsert ++ bccInsert ++ ccInsert)
    } yield () //emailInsert + numberOfRowsUpdated.sum
  }

  private[implementations] def upsertAddress(address: String): DBIO[String] = {
    for {
      existing <- AddressesTable.selectByAddress(address).result.headOption

      row = existing
        .getOrElse(AddressRow(genUUID, address))

      _ <- AddressesTable.all.insertOrUpdate(row)
    } yield row.addressId
  }

  private[implementations] def upsertEmailAddress(emailId: String, chatId: String, address: DBIO[String], participantType: String) =
    for {
      addressId <- address
      existing <- EmailAddressesTable.selectByEmailIdAddressAndType(Some(emailId), Some(addressId), Some(participantType))
        .result.headOption

      row = existing
        .getOrElse(EmailAddressRow(genUUID, emailId, chatId, addressId, participantType))

      insertOrUpdate <- EmailAddressesTable.all.insertOrUpdate(row)
    } yield insertOrUpdate

  private[implementations] def fromCreateChatDTOtoChatDTO(chat: CreateChatDTO): Chat = {
    val email = chat.email

    Chat(
      chatId = chat.chatId.getOrElse(""),
      subject = chat.subject.getOrElse(""),
      addresses = Set(email.from.orNull) ++ email.to.getOrElse(Set()) ++ email.bcc.getOrElse(Set()) ++ email.cc.getOrElse(Set()),
      overseers = Set(),
      emails = Seq(
        Email(
          emailId = email.emailId.getOrElse(""),
          from = email.from.orNull,
          to = email.to.getOrElse(Set()),
          bcc = email.bcc.getOrElse(Set()),
          cc = email.cc.getOrElse(Set()),
          body = email.body.getOrElse(""),
          date = email.date.getOrElse(""),
          sent = 0,
          attachments = Set())))
  }

  //region getChat auxiliary methods

  /**
   * Method to get data of a specific chat
   * @param chatId ID of chat
   * @return chat's data. In this case, just the subject
   */
  private[implementations] def getChatData(chatId: String, userId: String): Future[Option[(String, String, String)]] = {
    val query = getChatDataAddressQuery(chatId, userId)
    db.run(query.result.headOption)
  }

  /**
   * Method that takes a chat and user and gives a query for the chat's id and subject and the user's address
   * @param chatId The chat's id
   * @param userId The user's id
   * @return A query for the chat's id, it's subject and the user's address
   */
  private[implementations] def getChatDataAddressQuery(chatId: String, userId: String): Query[(ConstColumn[String], Rep[String], Rep[String]), (String, String, String), scala.Seq] =
    for {
      subject <- ChatsTable.all.filter(_.chatId === chatId).map(_.subject)
      _ <- UserChatsTable.all.filter(userChatRow => userChatRow.chatId === chatId && userChatRow.userId === userId)
      addressId <- UsersTable.all.filter(_.userId === userId).map(_.addressId)
      address <- AddressesTable.all.filter(_.addressId === addressId).map(_.address)
    } yield (chatId, subject, address)

  /**
   * Method that retrieves the emails of a specific chat that a user can see:
   * - If the user is a participant of the email (from, to, bcc, cc) (the case of the bcc will be handled afterwards)
   *   OR if the user is overseeing another user in the chat (has access to the same emails the oversee has,
   *   excluding the oversee's drafts)
   * - AND if email is draft (sent = 0), only the user with the "from" address can see it
   * @param chatId ID of the requested chat
   * @param userId ID of the user that requested the chat
   * @return for each email, returns a tuple (emailId, body, date, sent)
   */
  private def getEmailsQuery(chatId: String, userId: String, emailId: Option[String]) = {
    val query = getVisibleEmailsQuery(userId)
      .filter(_._1 === chatId)
      .filterOpt(emailId)(_._2 === _)
      .map {
        case (_, emailId, body, date, sent) => (emailId, body, date, sent)
      }

    // Because for chats where a user is participant and is also overseeing another user (or more than one),
    // the emails would be repeated
    query.distinct
  }

  /**
   * Method that, given the emailIds of the emails the user can see
   * @param userId ID of the user requesting a chat
   * @param emailIdsQuery query with the email IDs of the chat to show in the response, already filtered
   *                      by the email the user has permission to see (including its oversees)
   * @return for each participant of an email, returns a tuple with (emailId, participantType, address)
   */
  private def getEmailAddressesQuery(userId: String, emailIdsQuery: Query[Rep[String], String, scala.Seq]) = {
    val userAddressIdQuery = UsersTable.all.filter(_.userId === userId).map(_.addressId)

    for {
      userAddressId <- userAddressIdQuery
      emailId <- emailIdsQuery
      //from address of this email
      fromAddressId <- EmailAddressesTable.all
        .filter(ea => ea.emailId === emailId && ea.participantType === "from")
        .map(_.addressId)

      (participantType, addressId, address) <- EmailAddressesTable.all.join(AddressesTable.all)
        .on((emailAddressRow, addressRow) => {
          val userOverseesOfThisChat = getUserChatOverseesQuery(userId, emailAddressRow.chatId)
          emailAddressRow.emailId === emailId &&
            emailAddressRow.addressId === addressRow.addressId &&
            (emailAddressRow.participantType =!= "bcc" ||
              (
                emailAddressRow.addressId === userAddressId ||
                fromAddressId === userAddressId ||
                emailAddressRow.addressId.in(userOverseesOfThisChat) ||
                fromAddressId.in(userOverseesOfThisChat)))
        })
        .map {
          case (emailAddressRow, addressRow) =>
            (emailAddressRow.participantType, emailAddressRow.addressId, addressRow.address)
        }

    } yield (emailId, participantType, address)
  }

  /**
   * Method that takes the queries to get the emails and the email addresses,
   * groups the data by email and builds an Email DTO
   * @param emailsQuery query to get the chat's emails that the user can see
   * @param emailAddressesQuery query to get the email addresses involved in each email
   * @return all the addresses of the chat
   *         and a Seq of Email DTOs
   */
  private def groupEmailsAndAddresses(
    emailsQuery: Query[(Rep[String], Rep[String], Rep[String], Rep[Int]), (String, String, String, Int), scala.Seq],
    emailAddressesQuery: Query[(Rep[String], Rep[String], Rep[String]), (String, String, String), scala.Seq]): Future[(Set[String], scala.Seq[Email])] = {

    /** Emails **/
    val emails = db.run(emailsQuery.result)
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

    /** Futures: **/
    for {
      chatAddresses <- chatAddressesResult
      emails <- emails
      groupedAddresses <- groupedEmailAddresses
      attachments <- attachments
    } yield (
      chatAddresses.toSet,
      buildEmailDto(emails, groupedAddresses, attachments))
  }

  /**
   * Method that retrieves all the email addresses (that the user is allow to see) involved in the chat requested
   * and the sequence of all the emails of the chat that the user can see
   * @param chatId ID of the requested chat
   * @param userId ID of the user requesting the chat
   * @return a Future of the tuple (chatEmailAddresses, sequenceOfEmailDTOs)
   */
  private def getGroupedEmailsAndAddresses(chatId: String, userId: String, emailId: Option[String]): Future[(Set[String], Seq[Email])] = {
    // Query to get all the emails of this chat that the user can see
    val emailsQuery = getEmailsQuery(chatId, userId, emailId)

    // Query to get all the addresses involved in the emails of this chat
    // (emailId, participationType, address)
    val emailAddressesQuery = getEmailAddressesQuery(userId, emailsQuery.map(_._1))

    groupEmailsAndAddresses(emailsQuery, emailAddressesQuery)
  }

  /**
   * Method that retrieves all the IDs of the attachments of each email of the chat
   * @param emailsIds query with the IDs of the emails the user is allowed to see
   * @return a Future with a Map with the attachment IDs grouped by email ID
   */
  private def getEmailsAttachments(emailsIds: Query[Rep[String], String, scala.Seq]) = {
    val query = AttachmentsTable.all
      .filter(_.emailId in emailsIds)
      .map(attachment => (attachment.emailId, attachment.attachmentId))
    // (emailId, attachmentId)

    db.run(query.result)
      .map(_
        .groupBy(_._1)
        .mapValues(_.map(_._2)))
  }

  /**
   * Method that links and merges the emails with its addresses and attachments
   * @param emails Sequence of email tuples with (emailId, body, date, sent)
   * @param addresses Map of the email addresses grouped by email ID and participant Type
   * @param attachments Map of the attachment IDs grouped by email ID
   * @return a Sequence of Email(emailId, from, to, bcc, cc, body, date, sent, attachments) DTOs
   */
  private def buildEmailDto(
    emails: Seq[(String, String, String, Int)],
    addresses: Map[(String, String), Seq[String]],
    attachments: Map[String, Seq[String]]): Seq[Email] = {

    emails.map {
      case (emailId, body, date, sent) =>
        Email(
          emailId,
          addresses.getOrElse((emailId, "from"), Seq()).head,
          addresses.getOrElse((emailId, "to"), Seq()).toSet,
          addresses.getOrElse((emailId, "bcc"), Seq()).toSet,
          addresses.getOrElse((emailId, "cc"), Seq()).toSet,
          body,
          date,
          sent,
          attachments.getOrElse(emailId, Seq()).toSet)
    }
  }

  /**
   * Method that retrieves all the overseers of a specific chat grouped by the user who gave the oversight permission
   * @param chatId ID of the requested chat
   * @return a Future with a Sequence of Overseer(userAddress, overseersAddresses) DTOs
   */
  private def getOverseersData(chatId: String) = {
    val chatOverseersQuery = for {
      (overseerId, overseeId) <- OversightsTable.all.filter(_.chatId === chatId).map(row => (row.overseerId, row.overseeId))

      overseeAddressId <- UsersTable.all.filter(_.userId === overseeId).map(_.addressId)
      overseeAddress <- AddressesTable.all.filter(_.addressId === overseeAddressId)
        .map(_.address)

      overseerAddressId <- UsersTable.all.filter(_.userId === overseerId).map(_.addressId)
      overseerAddress <- AddressesTable.all.filter(_.addressId === overseerAddressId)
        .map(_.address)
    } yield (overseeAddress, overseerAddress)

    db.run(chatOverseersQuery.result)
      .map(_
        .groupBy(_._1) // group by user
        .mapValues(_.map(_._2).toSet)
        .toSeq
        .map(Overseers.tupled)
        .toSet)
  }
  //endregion

}