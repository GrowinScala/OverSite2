package utils

import model.dtos._
import model.types.ParticipantType
import org.scalacheck.Gen
import play.api.libs.json._

object TestGenerators {

  val genBoolean: Gen[Boolean] = Gen.oneOf(true, false)

  val genUUID: Gen[String] = Gen.uuid.map(_.toString)

  val genString: Gen[String] =
    for {
      stringSize <- Gen.choose(1, 7)
      charList <- Gen.listOfN(stringSize, Gen.alphaChar)
    } yield charList.mkString

  def genListOfT[T](f: Unit => T): Gen[List[T]] =
    for {
      listSize <- Gen.choose(1, 10)
      list <- Gen.listOfN(listSize, Gen.const(f))
    } yield list.map(_.apply(()))

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

  val genBadSignJSON: Gen[JsValue] =
    for {
      badAddress <- genString
      userAccessDTO <- genUserAccessDTO
    } yield Json.toJson(userAccessDTO.copy(address = badAddress, token = None))

  val genGoodSignJSON: Gen[JsValue] =
    genUserAccessDTO.map(userAccessDTO => Json.toJson(userAccessDTO.copy(token = None)))

  def genList[T](minElements: Int, maxElements: Int, elemenType: Gen[T]): Gen[Seq[T]] =
    for {
      size <- Gen.choose(minElements, maxElements)
      list <- Gen.listOfN(size, elemenType)
    } yield list

  val genChatPreviewDTO: Gen[ChatPreviewDTO] =
    for {
      chatId <- genUUID
      subject <- genString
      lastAddress <- genEmailAddress
      lastEmailDate <- genString
      contentPreview <- genString
    } yield ChatPreviewDTO(chatId, subject, lastAddress, lastEmailDate, contentPreview)

  val genChatPreviewDTOSeq: Gen[Seq[ChatPreviewDTO]] =
    genList(1, 3, genChatPreviewDTO)

  val genEmailDTO: Gen[EmailDTO] =
    for {
      emailId <- genUUID
      from <- genEmailAddress
      to <- genList(1, 4, genEmailAddress).map(_.toSet)
      bcc <- genList(0, 1, genEmailAddress).map(_.toSet)
      cc <- genList(0, 1, genEmailAddress).map(_.toSet)
      body <- genString
      date <- genString
      sent <- Gen.oneOf(true, false)
      attachments <- genList(0, 1, genString).map(_.toSet)
    } yield EmailDTO(emailId, from, to, bcc, cc, body, date, sent, attachments)

  def getaddresses(emails: Seq[EmailDTO]): Set[String] =
    emails.foldLeft(Set.empty[String])((set, emailDTO) =>
      set + emailDTO.from ++ emailDTO.to ++ emailDTO.bcc ++ emailDTO.cc)

  val genOverseersDTO: Gen[OverseersDTO] =
    for {
      user <- genEmailAddress
      overseers <- genList(1, 2, genEmailAddress).map(_.toSet)

    } yield OverseersDTO(user, overseers)

  val genChatDTO: Gen[ChatDTO] =
    genList(1, 4, genEmailDTO).flatMap(emails => {
      val addresses = emails.foldLeft(Set.empty[String])((set, emailDTO) =>
        set + emailDTO.from ++ emailDTO.to ++ emailDTO.bcc ++ emailDTO.cc)

      for {
        chatId <- genUUID
        subject <- genString
        overseers <- genList(0, 2, genOverseersDTO).map(_.toSet)
      } yield ChatDTO(chatId, subject, addresses, overseers, emails.sortBy(_.date))

    })

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

  val genCreateEmailDTOPost: Gen[UpsertEmailDTO] =
    for {
      from <- genEmailAddress
      to <- genList(1, 4, genEmailAddress).map(_.toSet)
      bcc <- genList(0, 1, genEmailAddress).map(_.toSet)
      cc <- genList(0, 1, genEmailAddress).map(_.toSet)
      body <- genString
      sent <- genBoolean
    } yield UpsertEmailDTO(None, Some(from), Some(to), Some(bcc), Some(cc), Some(body), None, Some(sent))

  val genCreateChatDTOption: Gen[CreateChatDTO] =
    for {
      chatId <- Gen.option(genUUID)
      subject <- Gen.option(genString)
      email <- genUpsertEmailDTOption
    } yield CreateChatDTO(chatId, subject, email)

  val genCreateChatDTOPost: Gen[CreateChatDTO] =
    for {
      subject <- genString
      email <- genCreateEmailDTOPost
    } yield CreateChatDTO(None, Some(subject), email)

  val genParticipantTypeTest3: Gen[Option[ParticipantType]] =
    Gen.oneOf(Some("from"), Some("to"))
      .map(_.flatMap(str => ParticipantType(str)))

  val genParticipantType: Gen[Option[ParticipantType]] =
    Gen.oneOf(Some("from"), Some("to"), Some("cc"), Some("bcc"), None)
      .map(_.flatMap(str => ParticipantType(str)))

}
