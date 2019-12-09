package utils

import model.dtos._
import model.types._
import model.types.Sort._
import Mailbox._
import model.types.ParticipantType._
import org.scalacheck.Gen
import play.api.libs.json._
import repositories.dtos._
import repositories.slick.implementations.{ BasicTestDB, OverseeingData, OverseenData, UserChatVisibilityData }
import repositories.slick.mappings._
import utils.DateUtils._
import repositories.RepUtils.types.OrderBy
import repositories.RepUtils.types.OrderBy._
import repositories.RepUtils.RepConstants._
import Gen._

object TestGenerators {

  val genBoolean: Gen[Boolean] = Gen.oneOf(true, false)
  val genBinary: Gen[Int] = Gen.oneOf(1, 0)

  val genUUID: Gen[String] = Gen.uuid.map(_.toString)

  val genString: Gen[String] =
    for {
      stringSize <- Gen.choose(5, 10)
      charList <- Gen.listOfN(stringSize, Gen.alphaChar)
    } yield charList.mkString

  val genEmailAddress: Gen[String] =
    for {
      name <- genString
      at = "@"
      domain <- genString
      dotCom = ".com"
    } yield List(name, at, domain, dotCom).mkString

  val genSimpleJsObj: Gen[JsValue] =
    for {
      field <- genString
      value <- genString

    } yield Json.obj(field -> value)

  val genUserAccessDTO: Gen[UserAccessDTO] =
    for {
      address <- genEmailAddress
      password <- genString
      first_name <- genString
      last_name <- genString
      token <- Gen.option(genUUID)
    } yield UserAccessDTO(address, password, Some(first_name), Some(last_name), token)

  val genUserAccess: Gen[UserAccess] =
    for {
      address <- genEmailAddress
      password <- genString
      first_name <- genString
      last_name <- genString
      token <- Gen.option(genUUID)
    } yield UserAccess(address, password, Some(first_name), Some(last_name), token)

  val genBadSignJSON: Gen[JsValue] =
    for {
      badAddress <- genString
      userAccessDTO <- genUserAccessDTO
    } yield Json.toJson(userAccessDTO.copy(address = badAddress, token = None))

  val genGoodSignJSON: Gen[JsValue] =
    genUserAccessDTO.map(userAccessDTO => Json.toJson(userAccessDTO.copy(token = None)))

  def genList[T](minElements: Int, maxElements: Int, elemenType: Gen[T]): Gen[List[T]] =
    for {
      size <- Gen.choose(minElements, maxElements)
      list <- Gen.listOfN(size, elemenType)
    } yield list

  val genChatPreviewDTO: Gen[ChatPreviewDTO] =
    for {
      chatId <- genUUID
      subject <- genString
      chatLink = s"http://localhost/chats/$chatId?page=$DEFAULT_PAGE&perPage=$DEFAULT_PER_PAGE&sort=$DEFAULT_SORT"
      lastAddress <- genEmailAddress
      lastEmailDate = getCurrentDate
      contentPreview <- genString
    } yield ChatPreviewDTO(chatId, chatLink, subject, lastAddress, lastEmailDate, contentPreview)

  val genChatPreviewDTOSeq: Gen[Seq[ChatPreviewDTO]] =
    genList(1, 3, genChatPreviewDTO)

  def genEmailDTO(chatId: String): Gen[EmailDTO] =
    for {
      emailId <- genUUID
      emailLink = s"http://localhost/chats/$chatId/emails/$emailId"
      from <- genEmailAddress
      to <- genList(1, 4, genEmailAddress).map(_.toSet)
      bcc <- genList(0, 1, genEmailAddress).map(_.toSet)
      cc <- genList(0, 1, genEmailAddress).map(_.toSet)
      body <- genString
      date = getCurrentDate
      sent <- Gen.oneOf(true, false)
      attachments <- genList(0, 1, genString).map(_.toSet)
    } yield EmailDTO(emailId, emailLink, from, to, bcc, cc, body, date, sent, attachments)

  val genOverseersDTO: Gen[OverseersDTO] =
    for {
      overseeAddress <- genEmailAddress
      overseersAddresses <- genList(1, 2, genEmailAddress).map(_.toSet)

    } yield OverseersDTO(overseeAddress, overseersAddresses)

  val genOverseers: Gen[Overseers] =
    for {
      overseeAddress <- genEmailAddress
      overseersAddresses <- genList(1, 2, genEmailAddress).map(_.toSet)

    } yield Overseers(overseeAddress, overseersAddresses)

  val genChatDTO: Gen[ChatDTO] =
    for {
      chatId <- genUUID
      emails <- genList(1, 4, genEmailDTO(chatId))

      addresses = emails.foldLeft(Set.empty[String])((set, emailDTO) =>
        set + emailDTO.from ++ emailDTO.to ++ emailDTO.bcc ++ emailDTO.cc)

      subject <- genString
      overseers <- genList(0, 2, genOverseersDTO).map(_.toSet)
    } yield ChatDTO(chatId, subject, addresses, overseers, emails.sortBy(_.date))

  val genUpsertEmailOption: Gen[UpsertEmail] =
    for {
      emailId <- Gen.option(genUUID)
      from <- genEmailAddress
      to <- Gen.option(genList(1, 4, genEmailAddress).map(_.toSet))
      bcc <- Gen.option(genList(0, 1, genEmailAddress).map(_.toSet))
      cc <- Gen.option(genList(0, 1, genEmailAddress).map(_.toSet))
      body <- Gen.option(genString)
      date <- Gen.option(genString)
      sent <- Gen.option(genBoolean)
    } yield UpsertEmail(emailId, Some(from), to, bcc, cc, body, date, sent)

  val genUpsertEmailDTOption: Gen[UpsertEmailDTO] =
    for {
      emailId <- Gen.option(genUUID)
      from <- genEmailAddress
      to <- Gen.option(genList(1, 4, genEmailAddress).map(_.toSet))
      bcc <- Gen.option(genList(0, 1, genEmailAddress).map(_.toSet))
      cc <- Gen.option(genList(0, 1, genEmailAddress).map(_.toSet))
      body <- Gen.option(genString)
      date <- Gen.option(genString)
      sent <- Gen.option(genBoolean)
    } yield UpsertEmailDTO(emailId, Some(from), to, bcc, cc, body, date, sent)

  val genCreateChatOption: Gen[CreateChat] =
    for {
      chatId <- Gen.option(genUUID)
      subject <- Gen.option(genString)
      email <- genUpsertEmailOption
    } yield CreateChat(chatId, subject, email)

  val genCreateChatDTOption: Gen[CreateChatDTO] =
    for {
      chatId <- Gen.option(genUUID)
      subject <- Gen.option(genString)
      email <- genUpsertEmailDTOption
    } yield CreateChatDTO(chatId, subject, email)

  val genAddressRow: Gen[AddressRow] =
    for {
      addressId <- genUUID
      address <- genEmailAddress
    } yield AddressRow(addressId, address)

  val genChatRow: Gen[ChatRow] =
    for {
      chatId <- genUUID
      subject <- genString
    } yield ChatRow(chatId, subject)

  def genUserRow(addressId: String): Gen[UserRow] =
    for {
      userId <- genUUID
      firstName <- genString
      lastName <- genString
    } yield UserRow(userId, addressId, firstName, lastName)

  def genEmailRow(chatId: String): Gen[EmailRow] =
    for {
      emailId <- genUUID
      body <- genString
      date = getCurrentDate
      sent <- genBinary
    } yield EmailRow(emailId, chatId, body, date, sent)

  def genEmailAddressRow(emailId: String, chatId: String, addressId: String, participantType: ParticipantType): Gen[EmailAddressRow] =
    for {
      emailAddressId <- genUUID
    } yield EmailAddressRow(emailAddressId, emailId, chatId, addressId, participantType)

  def genUserChatRow(userId: String, chatId: String): Gen[UserChatRow] =
    for {
      userChatId <- genUUID
    } yield UserChatRow(userChatId, userId, chatId, 1, 0, 0, 0)

  def genOversightRow(chatId: String, overseerId: String, overseeId: String): Gen[OversightRow] =
    for {
      oversightId <- genUUID
    } yield OversightRow(oversightId, chatId, overseerId, overseeId)

  val genBasicTestDB: Gen[BasicTestDB] =
    for {
      addressRow <- genAddressRow
      userRow <- genUserRow(addressRow.addressId)
      chatRow <- genChatRow
      emailRow <- genEmailRow(chatRow.chatId)
      emailAddressRow <- genEmailAddressRow(emailRow.emailId, chatRow.chatId, addressRow.addressId, From)
      userChatRow <- genUserChatRow(userRow.userId, chatRow.chatId)
    } yield BasicTestDB(addressRow, userRow, chatRow, emailRow, emailAddressRow, userChatRow)

  val genEmail: Gen[Email] =
    for {
      emailId <- genUUID
      from <- genEmailAddress
      to <- genList(1, 4, genEmailAddress).map(_.toSet)
      bcc <- genList(0, 1, genEmailAddress).map(_.toSet)
      cc <- genList(0, 1, genEmailAddress).map(_.toSet)
      body <- genString
      date = getCurrentDate
      sent <- genBinary
      attachments <- genList(0, 1, genString).map(_.toSet)
    } yield Email(emailId, from, to, bcc, cc, body, date, sent, attachments)

  val genChat: Gen[Chat] =
    genList(1, 4, genEmail).flatMap(emails => {
      val addresses = emails.foldLeft(Set.empty[String])((set, emailDTO) =>
        set + emailDTO.from ++ emailDTO.to ++ emailDTO.bcc ++ emailDTO.cc)

      for {
        chatId <- genUUID
        subject <- genString
        overseers <- genList(0, 2, genOverseers).map(_.toSet)
      } yield Chat(chatId, subject, addresses, overseers, emails.sortBy(_.date))
    })

  val genPostOverseerDTO: Gen[PostOverseerDTO] =
    for {
      address <- genEmailAddress
      optOversightId <- Gen.option(genUUID)
    } yield PostOverseerDTO(address, optOversightId)

  val genSeqPostOverseerDTO: Gen[Seq[PostOverseerDTO]] =
    genList(1, 4, genPostOverseerDTO)

  val genSetPostOverseerDTO: Gen[Set[PostOverseerDTO]] =
    genList(1, 4, genPostOverseerDTO).map(_.toSet)

  val genPostOverseer: Gen[PostOverseer] =
    for {
      address <- genEmailAddress
      optOversightId <- Gen.option(genUUID)
    } yield PostOverseer(address, optOversightId)

  val genSeqPostOverseer: Gen[Seq[PostOverseer]] =
    genList(1, 4, genPostOverseer)

  val genSetPostOverseer: Gen[Set[PostOverseer]] =
    genList(1, 4, genPostOverseer).map(_.toSet)

  val genPage: Gen[Page] =
    choose(1, 10).map(Page(_))

  val genPerPage: Gen[PerPage] =
    choose(1, 10).map(PerPage(_))

  val genMailbox: Gen[Mailbox] =
    oneOf(Inbox, Sent, Drafts, Trash)

  val genOverseeingDTO: Gen[OverseeingDTO] =
    for {
      oversightId <- genUUID
      overseeAddress <- genEmailAddress
    } yield OverseeingDTO(oversightId, overseeAddress)

  val genChatOverseeingDTO: Gen[ChatOverseeingDTO] =
    for {
      chatId <- genUUID
      overseeings <- genList(1, 3, genOverseeingDTO).map(_.toSet)
    } yield ChatOverseeingDTO(chatId, overseeings)

  val genSeqChatOverseeingDTO: Gen[Seq[ChatOverseeingDTO]] =
    genList(1, 3, genChatOverseeingDTO)

  val genOverseenDTO: Gen[OverseenDTO] =
    for {
      oversightId <- genUUID
      overseeAddress <- genEmailAddress
    } yield OverseenDTO(oversightId, overseeAddress)

  val genChatOverseenDTO: Gen[ChatOverseenDTO] =
    for {
      chatId <- genUUID
      overseens <- genList(1, 3, genOverseenDTO).map(_.toSet)
    } yield ChatOverseenDTO(chatId, overseens)

  val genSeqChatOverseenDTO: Gen[Seq[ChatOverseenDTO]] =
    genList(1, 3, genChatOverseenDTO)

  val genOversightDTO: Gen[OversightDTO] =
    for {
      overseeing <- option(genChatOverseeingDTO)
      overseen <- option(genChatOverseenDTO)
    } yield OversightDTO(overseeing, overseen)

  def genOverseeingData(chatId: String, overseerId: String): Gen[OverseeingData] =
    for {
      overseeAddressRow <- genAddressRow
      overseeUserRow <- genUserRow(overseeAddressRow.addressId)
      oversightRow <- genOversightRow(chatId, overseerId, overseeUserRow.userId)
    } yield OverseeingData(overseeAddressRow, overseeUserRow, oversightRow)

  def genOverseenData(chatId: String, overseeId: String): Gen[OverseenData] =
    for {
      overseerAddressRow <- genAddressRow
      overseerUserRow <- genUserRow(overseerAddressRow.addressId)
      oversightRow <- genOversightRow(chatId, overseerUserRow.userId, overseeId)
    } yield OverseenData(overseerAddressRow, overseerUserRow, oversightRow)

  def genUserChatVisibilityData(chatId: String, userId: String,
    userAddressId: String): Gen[UserChatVisibilityData] =
    for {
      emailRow <- genEmailRow(chatId)
      emailAddressRow <- genEmailAddressRow(emailRow.emailId, chatId, userAddressId, From)
      userChatRow <- genUserChatRow(userId, chatId)
    } yield UserChatVisibilityData(emailRow, emailAddressRow, userChatRow)

  val genOrderBy: Gen[OrderBy] =
    oneOf(Asc, Desc, DefaultOrder)

  def genSort(sortBy: String): Gen[Sort] =
    for {
      orderBy <- genOrderBy
    } yield Sort(sortBy, orderBy)

  val genAttachmentInfoDTO: Gen[AttachmentInfoDTO] =
    for {
      attachmentId <- genUUID
      filename <- genString
    } yield AttachmentInfoDTO(filename, attachmentId)

  val genSetAttachmentInfoDTO: Gen[Set[AttachmentInfoDTO]] =
    genList(1, 4, genAttachmentInfoDTO).map(_.toSet)

  val genAttachmentInfo: Gen[AttachmentInfo] =
    for {
      attachmentId <- genUUID
      filename <- genString
    } yield AttachmentInfo(filename, attachmentId)

  val genSetAttachmentInfo: Gen[Set[AttachmentInfo]] =
    genList(1, 4, genAttachmentInfo).map(_.toSet)
}
