package services

import java.nio.file.{ Files, Path, Paths }
import java.io.File

import model.dtos._
import model.dtos.PostOverseerDTO._
import model.dtos.EmailDTO._
import model.dtos.ChatDTO._
import org.mockito.scalatest.AsyncIdiomaticMockito
import org.scalacheck.Gen
import org.scalatest._
import repositories.ChatsRepository
import repositories.dtos.PatchChat
import OversightDTO._
import ChatPreviewDTO._
import Gen._
import akka.stream.scaladsl.FileIO
import com.typesafe.config.ConfigFactory
import controllers.AuthenticatedUser
import model.types._
import repositories.RepUtils.RepMessages._
import utils.Jsons._
import model.dtos.ChatOverseeingDTO._
import model.dtos.ChatOverseenDTO._
import play.api.test.FakeRequest
import play.api.Configuration
import utils.FileUtils

import scala.concurrent.Future
import utils.TestGenerators._

class ChatServiceSpec extends AsyncWordSpec with BeforeAndAfterAll
  with AsyncIdiomaticMockito with MustMatchers with OptionValues {

  implicit val config: Configuration = Configuration(ConfigFactory.load("application.conf"))

  def getServiceAndRepMock: (ChatService, ChatsRepository) = {
    implicit val mockChatsRep: ChatsRepository = mock[ChatsRepository]
    val chatServiceImpl = new ChatService()
    (chatServiceImpl, mockChatsRep)
  }

  override def beforeAll(): Unit = {
    FileUtils.createDirectory(config.get[String]("uploadDirectory"))
  }

  override def afterAll(): Unit = {
    FileUtils.deleteRecursively(new File(config.get[String]("uploadDirectory")))
  }

  "ChatService#getChats" should {
    "map the repository's result" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      val optTestChatsPreviewDTO = Gen.option(genChatPreviewDTOSeq).sample.value
      val optChatsPreview = optTestChatsPreviewDTO.map(toSeqChatPreview)
      val totalCount = choose(1, 10).sample.value
      val lastPage = choose(1, 10).sample.value

      mockChatsRep.getChatsPreview(*, *, *, *, *)
        .returns(Future.successful(optChatsPreview.map((_, totalCount, lastPage))))

      val chatsPreviewDTO = chatService.getChats(genMailbox.sample.value, genPage.sample.value,
        genPerPage.sample.value, genString.flatMap(genSort).sample.value,
        AuthenticatedUser(genString.sample.value, FakeRequest()))
      chatsPreviewDTO.map(_ mustBe optTestChatsPreviewDTO.map((_, totalCount, Page(lastPage))))
    }
  }

  "ChatService#getChat" should {
    "map the repository's Right result" in {

      val testchatDTO = genChatDTO.sample.value
      val totalCount = choose(1, 10).sample.value
      val lastPage = choose(1, 10).sample.value

      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.getChat(*, *, *, *, *)
        .returns(Future.successful(Right(toChat(testchatDTO), totalCount, lastPage)))

      val serviceResponse = chatService.getChat(genUUID.sample.value, genPage.sample.value,
        genPerPage.sample.value, genString.flatMap(genSort).sample.value,
        AuthenticatedUser(genString.sample.value, FakeRequest()))

      serviceResponse.map(_ mustBe Right(testchatDTO, totalCount, Page(lastPage)))
    }

    "return chatNotFound according to the repository's response" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.getChat(*, *, *, *, *)
        .returns(Future.successful(Left(CHAT_NOT_FOUND)))

      val serviceResponse = chatService.getChat(genUUID.sample.value, genPage.sample.value,
        genPerPage.sample.value, genString.flatMap(genSort).sample.value,
        AuthenticatedUser(genString.sample.value, FakeRequest()))

      serviceResponse.map(_ mustBe Left(chatNotFound))
    }

    "return InternalServerError if the repository returns an error message other than chatNotFound" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.getChat(*, *, *, *, *)
        .returns(Future.successful(Left(genString.sample.value)))

      val serviceResponse = chatService.getChat(genUUID.sample.value, genPage.sample.value,
        genPerPage.sample.value, genString.flatMap(genSort).sample.value,
        AuthenticatedUser(genString.sample.value, FakeRequest()))

      serviceResponse.map(_ mustBe Left(internalError))
    }

  }

  "ChatService#postChat" should {
    "return a CreateChatDTO equal to the input plus a new chatId and a new emailID" in {
      val createChatDTO = genCreateChatDTOption.sample.value

      val expectedRepoResponse = CreateChatDTO.toCreateChat(createChatDTO)

      val expectedServiceResponse = CreateChatDTO.toCreateChatDTO(expectedRepoResponse)

      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.postChat(*, *)
        .returns(
          Future.successful(Some(expectedRepoResponse)))

      val serviceResponse = chatService.postChat(createChatDTO, genUUID.sample.value)

      serviceResponse.map(_ mustBe Some(expectedServiceResponse))
    }

  }

  "ChatService#postEmail" should {
    "return a CreateChatDTO that contains the input emailDTO plus the chatId and a new emailID" in {
      val upsertEmailDTO = genUpsertEmailDTOption.sample.value

      val expectedResponse = CreateChatDTO.toCreateChat(genCreateChatDTOption.sample.value)

      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.postEmail(*, *, *)
        .returns(Future.successful(Some(expectedResponse)))

      val serviceResponse = chatService.postEmail(upsertEmailDTO, genUUID.sample.value, genUUID.sample.value)

      serviceResponse.map(_ mustBe Some(CreateChatDTO.toCreateChatDTO(expectedResponse)))
    }
  }

  "ChatService#patchChat" should {
    "return some MoveToTrash DTO if the ChatsRepository returns some MoveToTrash DTO" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.patchChat(PatchChat.MoveToTrash, *, *)
        .returns(Future.successful(Some(PatchChat.MoveToTrash)))

      val moveChatToTrashService = chatService
        .patchChat(PatchChatDTO.MoveToTrash, genUUID.sample.value, genUUID.sample.value)
      moveChatToTrashService.map(_ mustBe Some(PatchChatDTO.MoveToTrash))
    }
    "return some Restore DTO if the ChatsRepository returns some Restore DTO" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.patchChat(PatchChat.Restore, *, *)
        .returns(Future.successful(Some(PatchChat.Restore)))

      val moveChatToTrashService = chatService
        .patchChat(PatchChatDTO.Restore, genUUID.sample.value, genUUID.sample.value)
      moveChatToTrashService.map(_ mustBe Some(PatchChatDTO.Restore))
    }
    "return some ChangeSubject DTO if the ChatsRepository returns some ChangeSubject DTO" in {
      val newSubject = "New Subject"
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.patchChat(PatchChat.ChangeSubject(newSubject), *, *)
        .returns(Future.successful(Some(PatchChat.ChangeSubject(newSubject))))

      val moveChatToTrashService = chatService
        .patchChat(PatchChatDTO.ChangeSubject(newSubject), genUUID.sample.value, genUUID.sample.value)
      moveChatToTrashService.map(_ mustBe Some(PatchChatDTO.ChangeSubject(newSubject)))
    }
    "return None if the ChatsRepository returns None" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.patchChat(*, *, *)
        .returns(Future.successful(None))

      val moveChatToTrashService = chatService
        .patchChat(PatchChatDTO.MoveToTrash, genUUID.sample.value, genUUID.sample.value)
      moveChatToTrashService.map(_ mustBe None)
    }
  }

  "ChatService#patchEmail" should {
    "return an EmailDTO that contains the email with the updated/patched fields" in {
      val returnedEmail = genEmail.sample.value
      val chatId = genUUID.sample.value
      val authenticatedUser = AuthenticatedUser(genString.sample.value, FakeRequest())

      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.patchEmail(*, *, *, *)
        .returns(Future.successful(Some(returnedEmail)))

      val serviceResponse = chatService.patchEmail(
        genUpsertEmailDTOption.sample.value,
        chatId, genUUID.sample.value, authenticatedUser)

      serviceResponse.map(_ mustBe toEmailDTO(chatId, Some(returnedEmail), authenticatedUser))
    }
  }

  "ChatService#getEmail" should {
    "return a ChatDTO with the requested email" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      val authenticatedUser = AuthenticatedUser(genString.sample.value, FakeRequest())
      val repositoryChatResponse = genChat.sample.value

      mockChatsRep.getEmail(*, *, *)
        .returns(Future.successful(Some(repositoryChatResponse)))

      val expectedServiceResponse = Some(toChatDTO(repositoryChatResponse, authenticatedUser))

      chatService.getEmail(genUUID.sample.value, genUUID.sample.value, authenticatedUser).map(
        serviceResponse => serviceResponse.value mustBe expectedServiceResponse.value)
    }
  }

  "ChatService#deleteChat" should {
    "return true if the ChatsRepository returns true" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.deleteChat(*, *)
        .returns(Future.successful(true))

      val deleteChatService = chatService.deleteChat(genUUID.sample.value, genUUID.sample.value)
      deleteChatService.map(_ mustBe true)
    }
    "return false if the ChatsRepository returns false" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.deleteChat(*, *)
        .returns(Future.successful(false))

      val deleteChatService = chatService.deleteChat(genUUID.sample.value, genUUID.sample.value)
      deleteChatService.map(_ mustBe false)
    }
  }

  "ChatService#deleteDraft" should {
    "return true if the ChatsRepository returns true" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.deleteDraft(*, *, *)
        .returns(Future.successful(true))

      val deleteDraftService = chatService.deleteDraft(
        genUUID.sample.value, genUUID.sample.value, genUUID.sample.value)
      deleteDraftService.map(_ mustBe true)
    }
    "return false if the ChatsRepository returns false" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.deleteDraft(*, *, *)
        .returns(Future.successful(false))

      val deleteDraftService = chatService.deleteDraft(
        genUUID.sample.value, genUUID.sample.value, genUUID.sample.value)
      deleteDraftService.map(_ mustBe false)
    }
  }

  "ChatService#postOverseers" should {
    "turn the received optional Set of PostOverseer to one of PostOverseerDTO" in {

      val expectedResponse = genSetPostOverseerDTO.sample

      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.postOverseers(*, *, *)
        .returns(Future.successful(expectedResponse.map(toSetPostOverseer)))

      val serviceResponse = chatService.postOverseers(
        genSetPostOverseerDTO.sample.value,
        genUUID.sample.value, genUUID.sample.value)

      serviceResponse.map(_ mustBe expectedResponse)
    }
  }

  "ChatService#getOverseers" should {
    "map the repository's Right result" in {

      val postOverseersDTO = genSeqPostOverseerDTO.sample.value
      val totalCount = choose(1, 10).sample.value
      val lastPage = choose(1, 10).sample.value

      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.getOverseers(*, *, *, *, *)
        .returns(Future.successful(Right(toSeqPostOverseer(postOverseersDTO), totalCount, lastPage)))

      val serviceResponse = chatService.getOverseers(genUUID.sample.value, genPage.sample.value,
        genPerPage.sample.value, genString.flatMap(genSort).sample.value, genUUID.sample.value)

      serviceResponse.map(_ mustBe Right(postOverseersDTO, totalCount, Page(lastPage)))
    }

    "return chatNotFound according to the repository's response" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.getOverseers(*, *, *, *, *)
        .returns(Future.successful(Left(CHAT_NOT_FOUND)))

      val serviceResponse = chatService.getOverseers(genUUID.sample.value, genPage.sample.value,
        genPerPage.sample.value, genString.flatMap(genSort).sample.value, genUUID.sample.value)

      serviceResponse.map(_ mustBe Left(chatNotFound))
    }

    "return InternalServerError if the repository returns an error message other than chatNotFound" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.getOverseers(*, *, *, *, *)
        .returns(Future.successful(Left(CHAT_NOT_FOUND)))

      val serviceResponse = chatService.getOverseers(genUUID.sample.value, genPage.sample.value,
        genPerPage.sample.value, genString.flatMap(genSort).sample.value, genUUID.sample.value)

      serviceResponse.map(_ mustBe Left(chatNotFound))

    }
  }

  "ChatService#deleteOverseer" should {
    "return true if the ChatsRepository returns true" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.deleteOverseer(*, *, *)
        .returns(Future.successful(true))

      val deleteOverseerService = chatService.deleteOverseer(
        genUUID.sample.value, genUUID.sample.value, genUUID.sample.value)
      deleteOverseerService.map(_ mustBe true)
    }
    "return false if the ChatsRepository returns false" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.deleteOverseer(*, *, *)
        .returns(Future.successful(false))

      val deleteOverseerService = chatService.deleteOverseer(
        genUUID.sample.value, genUUID.sample.value, genUUID.sample.value)
      deleteOverseerService.map(_ mustBe false)
    }
  }

  "ChatService#getOversights" should {
    "turn the received optional Oversight to OversightDTO" in {

      val expectedResponse = option(genOversightDTO).sample.value

      val (chatService, mockChatsRep) = getServiceAndRepMock
      mockChatsRep.getOversights(*)
        .returns(Future.successful(expectedResponse.map(toOversight)))

      val serviceResponse = chatService.getOversights(genUUID.sample.value)

      serviceResponse.map(_ mustBe expectedResponse)
    }
  }

  "ChatService#getOverseeings" should {
    "map the repository's optional response" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      val optSeqChatOverseeingDTO = Gen.option(genSeqChatOverseeingDTO).sample.value
      val optSeqChatOverseeing = optSeqChatOverseeingDTO.map(toSeqChatOverseeing)
      val totalCount = choose(1, 10).sample.value
      val lastPage = choose(1, 10).sample.value

      mockChatsRep.getOverseeings(*, *, *, *)
        .returns(Future.successful(optSeqChatOverseeing.map((_, totalCount, lastPage))))

      val seqChatOverseeingDTO = chatService.getOverseeings(genPage.sample.value, genPerPage.sample.value,
        genString.flatMap(genSort).sample.value, genUUID.sample.value)
      seqChatOverseeingDTO.map(_ mustBe optSeqChatOverseeingDTO.map((_, totalCount, Page(lastPage))))
    }
  }

  "ChatService#getOverseens" should {
    "map the repository's optional response" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      val optSeqChatOverseenDTO = Gen.option(genSeqChatOverseenDTO).sample.value
      val optSeqChatOverseen = optSeqChatOverseenDTO.map(toSeqChatOverseen)
      val totalCount = choose(1, 10).sample.value
      val lastPage = choose(1, 10).sample.value

      mockChatsRep.getOverseens(*, *, *, *)
        .returns(Future.successful(optSeqChatOverseen.map((_, totalCount, lastPage))))

      val seqChatOverseenDTO = chatService.getOverseens(
        genPage.sample.value,
        genPerPage.sample.value, genString.flatMap(genSort).sample.value, genUUID.sample.value)
      seqChatOverseenDTO.map(_ mustBe optSeqChatOverseenDTO.map((_, totalCount, Page(lastPage))))
    }
  }

  "ChatService#uploadAttachment" should {
    "correctly save the attachment into the default upload directory" in {
      val (chatService, _) = getServiceAndRepMock

      val filename = genString.sample.value
      val file = FileUtils.generateTextFile(filename)
      val filePath: Path = file.toPath
      file.deleteOnExit()

      val source = FileIO.fromPath(filePath)

      val hashBeforeUpload: String = FileUtils.computeHash(filePath.toString)

      chatService.uploadAttachment(source).map { optionPath =>
        val path: String = optionPath.value
        val hashAfterUpload: String = FileUtils.computeHash(path)

        hashAfterUpload mustBe hashBeforeUpload
      }
    }
  }

  "ChatService#postAttachment" should {
    "return the attachmentId of the file attached" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      val attachmentId = genUUID.sample.value
      val filename = genString.sample.value
      val contentType = genString.sample.value

      mockChatsRep.verifyDraftPermissions(*, *, *)
        .returns(Future.successful(true))

      mockChatsRep.postAttachment(*, *, *, *, *, *)
        .returns(Future.successful(attachmentId))

      val file = FileUtils.generateTextFile(filename)
      file.deleteOnExit()
      val source = FileIO.fromPath(file.toPath)

      chatService
        .postAttachment(genUUID.sample.value, genUUID.sample.value,
          genUUID.sample.value, filename, Some(contentType), source)
        .map(_ mustBe Some(attachmentId))
    }
    "return None if the user does not have permission to attach a file" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      val filename = genString.sample.value
      val contentType = genString.sample.value

      mockChatsRep.verifyDraftPermissions(*, *, *)
        .returns(Future.successful(false))

      val file = FileUtils.generateTextFile(filename)
      file.deleteOnExit()
      val source = FileIO.fromPath(file.toPath)

      chatService
        .postAttachment(genUUID.sample.value, genUUID.sample.value,
          genUUID.sample.value, filename, Some(contentType), source)
        .map(_ mustBe None)

    }
  }
  "ChatService#getAttachments" should {
    "return some set of AttachmentInfoDTOs" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      val authenticatedUser = AuthenticatedUser(genString.sample.value, FakeRequest())
      val setAttachmentInfo = genSetAttachmentInfo.sample.value

      mockChatsRep.getAttachments(*, *, *)
        .returns(Future.successful(Some(setAttachmentInfo)))

      val expectedServiceResponse = Some(setAttachmentInfo.map(AttachmentInfoDTO.toAttachmentInfoDTO))

      chatService.getAttachments(genUUID.sample.value, genUUID.sample.value, authenticatedUser.userId).map(
        serviceResponse => serviceResponse.value mustBe expectedServiceResponse.value)
    }

    "return None if the user does not have permission see the attachments info" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      val authenticatedUser = AuthenticatedUser(genString.sample.value, FakeRequest())

      mockChatsRep.getAttachments(*, *, *)
        .returns(Future.successful(None))

      chatService.getAttachments(genUUID.sample.value, genUUID.sample.value, authenticatedUser.userId).map(
        serviceResponse => serviceResponse mustBe None)
    }
  }

  "ChatService#deleteAttachment" should {
    "return true if the attachment was deleted" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      val authenticatedUser = AuthenticatedUser(genString.sample.value, FakeRequest())
      val filename = genString.sample.value

      val file = FileUtils.generateTextFile(filename)
      val path = file.toPath

      Files.exists(path) mustBe true

      mockChatsRep.deleteAttachment(*, *, *, *)
        .returns(Future.successful(Some(path.toString)))

      chatService
        .deleteAttachment(genUUID.sample.value, genUUID.sample.value,
          genUUID.sample.value, authenticatedUser.userId)
        .map { response =>
          response mustBe true
          Files.exists(path) mustBe false
        }
    }

    "return false if the attachment was not deleted" in {
      val (chatService, mockChatsRep) = getServiceAndRepMock
      val authenticatedUser = AuthenticatedUser(genString.sample.value, FakeRequest())

      mockChatsRep.deleteAttachment(*, *, *, *)
        .returns(Future.successful(None))

      chatService
        .deleteAttachment(genUUID.sample.value, genUUID.sample.value,
          genUUID.sample.value, authenticatedUser.userId)
        .map(_ mustBe false)
    }
  }
}
