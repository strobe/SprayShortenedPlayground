package cc.evgeniy.shortened

import akka.actor.{ActorRef, ActorLogging, Actor}
import com.github.tminglei.slickpg.InetString
import org.joda.time.DateTime
import spray.http.{HttpEntity, HttpResponse}
import spray.http.MediaTypes._
import spray.http.StatusCodes._
import spray.json.{JsNumber, JsString, JsObject}
import spray.routing
import spray.routing._
import spray.json._
import org.joda.time.DateTime
import org.joda.time._
import scala.concurrent.duration._
import org.apache.commons.validator.routines.UrlValidator
import com.github.tminglei.slickpg.InetString

import cc.evgeniy.shortened.ExtendedPostgresDriver.simple._
import com.github.tototoshi.slick.PostgresJodaSupport._
import scala.slick.lifted
import com.typesafe.config.ConfigFactory
import cc.evgeniy.shortened.RequestParams._
import RequestParams.SourceLinkJsonSupport._
import scala.concurrent.ExecutionContextExecutor


class ShortenedControllerActor extends Actor with ActorLogging {

  // we use the enclosing ActorContext's or ActorSystem's dispatcher
  // for our Futures and Scheduler
  implicit def executionContext: ExecutionContextExecutor = context.dispatcher

  val controller = new ApiController

  def receive = {

    case AskTokenResponse(user_id: Int) => {
      sender ! controller.doTokenResponse(user_id)
    }

    case AskPostLinkResponse(link: SourceLinkParameter) => {
      sender ! controller.doPostLinkResponse(link)
    }

    case AskGetLinksResponse(token: String, offset: Int, limit: Int) => {
      sender ! controller.doGetLinksResponse(token, offset, limit)
    }

    case AskGetLinkResponse(token: String, code: String) => {
      sender ! controller.doGetLinkResponse(token, code)
    }

    case AskGetClicksResponse(token: String, code: String,
                              offset: Int, limit: Int) => {
      sender ! controller.doGetClicksResponse(token, code, offset, limit)
    }

    case AskGetFolderResponse(token: String) => {
      sender ! controller.doGetFolderResponse(token)
    }

    case AskGetFolderResponseById(token: String, id: Int,
                                  offset: Int, limit: Int) => {
      sender ! controller.doGetFolderResponseById(token, id, offset, limit)
    }

    case AskAddNewClicks(code: String, click: ClickParameter) => {
      sender ! controller.doNewClickResponse(code, click)
    }

    case _ => // ignore
  }

}


class ApiController extends UsersHashIDs with UrlCodec {

  import Dao._

  def doNewClickResponse(code: String, click: ClickParameter) = {
    addNewClicks(code, click)
  }


  def doGetFolderResponse(token: String) = {
    val folders: Seq[Folder] = getFoldersByUserToken(token)

    import FoldersJsonProtocol._

    val jsonFolders = for {f <- folders} yield f.toJson

    HttpResponse(entity = HttpEntity(`application/json`,
      JsObject("folders" -> jsonFolders.toJson).prettyPrint))
  }


  def doGetFolderResponseById(token: String, id: Int, offset: Int, limit: Int) = {
    val offset0: Int = if (offset > 0) offset else 0
    val limit0: Int = if (limit > 0) limit else 25

    val links: Seq[Link] = {
      getFoldersLinksByLimitAndOffset(token, id, limit0, offset0)
    }

    if (links.length > 0) {
      import LinksJsonProtocol._

      val jsonLinks = for {l <- links} yield l.toJson

      HttpResponse(entity = HttpEntity(`application/json`,
                                        JsObject("links" -> jsonLinks.toJson)
                                          .prettyPrint))
    }
    else {
      HttpResponse(NotFound)
    }
  }


  def doGetClicksResponse(token: String, code: String, offset: Int, limit: Int) = {
    val offset0 = if (offset > 0) offset else 0
    val limit0 = if (limit > 0) limit else 25

    val clicks =  getClicksByLimitAndOffset(token, limit0, offset0)

    import ClicksJsonProtocol._

    val jsonClicks = for {c <- clicks} yield c.toJson

    HttpResponse(entity = HttpEntity(`application/json`,
      JsObject("clicks" -> jsonClicks.toJson).prettyPrint))
  }


  def doGetLinkResponse(token: String, code: String) = {
    val link : Option[Link] = getLinkByCode(token, code)

    link match {
      case Some(l) => {
        val clicks = getClicksByLinkId(l.id.get)
        // getting folders
        val folderLink: Option[FolderLink] = getFolderLinkByLinkId(l.id.get)
        val folder: Option[Folder] = folderLink match {
            case Some(fl) => getFolderById(fl.folder_id)
            case None => None
          }

        def doJson(folder: Option[Folder]): String =  {
          folder match {
            case Some(f) => {
              JsObject("link" -> JsObject(
                "url" -> JsString(l.url),
                "code" -> JsString(l.code)),
                "clicks" -> JsNumber(clicks.length),
                "folder" -> JsString(f.title)).prettyPrint
            }
            case None => {
              JsObject("link" -> JsObject(
                "url" -> JsString(l.url),
                "code" -> JsString(l.code)),
                "clicks" -> JsNumber(clicks.length)).prettyPrint
            }
          }
        }

        HttpResponse(entity = HttpEntity(`application/json`,doJson(folder)))
      }
      case None => HttpResponse(NotFound)
    }
  }


  def doGetLinksResponse(token: String, offset: Int, limit: Int) = {
    val offset0 = if (offset > 0) offset else 0
    val limit0 = if (limit > 0) limit else 25

    val links: Seq[Link] = {
      getLinksByLimitAndOffset(token: String, offset: Int, limit: Int)
    }

    import LinksJsonProtocol._

    val jsonLinks = for {l <- links} yield l.toJson

    HttpResponse(entity = HttpEntity(`application/json`,
      JsObject("links" -> jsonLinks.toJson).prettyPrint))
  }


  def doPostLinkResponse(link: SourceLinkParameter)= {
    // Make new folder and link //
    def createFolderForLink(link0: Option[Link]) {
      // adding new folder
      val folder: Option[Folder] = link.folder_id match {
        case Some(title: String) =>
          addNewFolder(link.token, title)
        case None => None
      }

      // adding new link to folder connection
      try {
        val folder0: Int = folder.get.id.get
        val id: Int = link0.get.id.get
        connectLinkWithFolder(folder0, id)
      } catch {
        case e: Exception =>
          HttpResponse(UnprocessableEntity,
            s"new folder link creation failed:\n ${e.toString}")
      }
    }

    isLinkExist(link.token, link.url) match {
      case true => {
        getLinkByUrl(link.token, link.url) match {
          case Some(l) => completeLinkAsJson (l.url, l.code)
          case None    => HttpResponse(NotFound)
        }
      }

      case false => {
        val newLink: Option[Link] = link.code match {  // code parameter is empty
          case None => {
            // adding new link
            val link0: Option[Link] = addNewHashLink(link.token, link.url)
            createFolderForLink(link0)
            link0
          }

          case Some(code) => {
            val link0: Option[Link] = addNewUserLink(link.token, link.url, code)
            createFolderForLink(link0)
            link0
          }
        }

        newLink match {
          case Some(l) => completeLinkAsJson(link.url, l.code)
          case None => HttpResponse(UnprocessableEntity)
        }
      }
    }
  }


  def doTokenResponse(user_id: Int) = {
    val token: String = hashids.encode(user_id)

    val users: Seq[User] = getUsersByToken(token)

    users.isEmpty match {
        case false => {
          HttpResponse(entity = HttpEntity(`application/json`,
            JsObject("token" -> JsString(users.seq.head.token)).prettyPrint))
        }
        case true => {
          addUser(token)
          HttpResponse(entity = HttpEntity(`application/json`,
            JsObject("token" -> JsString(token)).prettyPrint))
        }
      }
  }


  ////////////// helpers //////////////


  def completeLinkAsJson(url: String, code: String) = {
    HttpResponse(entity = HttpEntity(`application/json`,
      JsObject("link" -> JsObject(
        "url" -> JsString(url),
        "code" -> JsString(code)
      )).prettyPrint))
  }


  def getUserId(token: String): Long = {
    hashids.decode(token).head
  }


  def getToken(user_id: Long): String = {
    hashids.encode(user_id)
  }


  def addNewHashLink(token: String, url: String): Option[Link] = {
    val code = makeNewUrlCode(token, url)

    addNewUserLink(token, url, code)
  }


  def makeNewUrlCode(token: String,
                     url: String,
                     isUserLink: Boolean = false,
                     userLink: Option[String] = None): String = {
    val last: Option[Long] = getLastLinkCode(token) match {
      case Some(c) => decode(c).headOption
      case None => None
    }

    // return intit code if no any link doesn't exist and new if exist
    last match {
      case Some(id) =>
        encode(id + 1L)
      case None =>
        encode(1L)
    }
  }

}