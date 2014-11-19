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
      sender ! controller.addNewClicks(code, click)
    }

    case _ => // ignore
  }

}

class ApiController extends UsersHashIDs with UrlCodec {

  import Dao._

  def addNewClicks(code: String, click: ClickParameter): Click = {
    val clicks = addNewClicks(code, click.referer, click.remote_ip)
    clicks.head
  }


  def doGetFolderResponse(token: String) = {
    val folders: Seq[Folder] = db withSession { implicit session =>
      (for {
        u <- Users   if u.token === token
        f <- Folders if f.user_id === u.id
      } yield f).run
    }


    import FoldersJsonProtocol._
    val jsonFolders = for {f <- folders} yield f.toJson
    HttpResponse(entity = HttpEntity(`application/json`,
      JsObject("folders" -> jsonFolders.toJson).prettyPrint))
  }


  def doGetFolderResponseById(token: String, id: Int, offset: Int, limit: Int) = {
    val offset0: Int = if (offset > 0) offset else 0
    val limit0: Int = if (limit > 0) limit else 25

    val links: Seq[Link] = db withSession { implicit session =>
      (for {
        u <- Users if u.token === token
        f <- Folders if f.user_id === u.id && f.id === id
        fl <- FolderLinks if fl.folder_id === f.id
        l <- Links if l.id === fl.link_id
      } yield l)
        .run
        .drop(offset0).take(limit0)
    }

    if (links.length > 0) {
      import LinksJsonProtocol._
      val jsonLinks = for {l <- links} yield l.toJson
      HttpResponse(entity = HttpEntity(`application/json`,
                                        JsObject("links" -> jsonLinks.toJson).prettyPrint))
    }
    else {
      HttpResponse(NotFound)
    }
  }


  def doGetClicksResponse(token: String, code: String, offset: Int, limit: Int) = {
    val offset0 = if (offset > 0) offset else 0
    val limit0 = if (limit > 0) limit else 25

    val clicks = db withSession { implicit session =>
      val existClicks: Seq[Click] = (for {
        u <- Users if u.token === token
        l <- Links if l.user_id === u.id
        c <- Clicks if c.link_id === l.id
      } yield c).run

      existClicks.drop(offset0).take(limit0)
    }

    import ClicksJsonProtocol._
    val jsonClicks = for {c <- clicks} yield c.toJson
    HttpResponse(entity = HttpEntity(`application/json`,
      JsObject("clicks" -> jsonClicks.toJson).prettyPrint))
  }


  def doGetLinkResponse(token: String, code: String) = {
    db withSession { implicit session =>
      val link : Option[Link] = (for {
        u <- Users if u.token === token
        l <- Links if l.user_id === u.id && l.code === code
      } yield l).run.headOption


      link match {
        case Some(l) => {
          val clicks: Seq[Click] = Clicks.findByLinkId(l.id.get).run
          // getting folders
          val folderLink: Option[FolderLink] = FolderLinks.findByLinkId(l.id.get).run.headOption
          val folder: Option[Folder] = folderLink match {
            case Some(fl) => Folders.findById(fl.folder_id).run.headOption
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
  }

  def doGetLinksResponse(token: String, offset: Int, limit: Int) = {
    val offset0 = if (offset > 0) offset else 0
    val limit0 = if (limit > 0) limit else 25

    val links = db withSession { implicit session =>
      val existLinks: Seq[Link] = (for {
        u <- Users if u.token === token
        l <- Links if l.user_id === u.id
      } yield l).run
      existLinks.drop(offset0).take(limit0)
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
        getLink(link.token, link.url) match {
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

    db withSession { implicit session =>
      val query = TableQuery[Users].filter(_.token === token).run
      query.isEmpty match {
        case false => {
          HttpResponse(entity = HttpEntity(`application/json`,
            JsObject("token" -> JsString(query.seq.head.token)).prettyPrint))
        }
        case true => {
          Users insert User(None, token)
          HttpResponse(entity = HttpEntity(`application/json`,
            JsObject("token" -> JsString(token)).prettyPrint))
        }
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


  def isUserTokenCorrect(token: String) = {
    db withSession { implicit session =>
      val query: Seq[Users#TableElementType] = TableQuery[Users].filter(_.token === token).run
      query.isEmpty match {
        case false => {
          true
        }
        case true => {
          false
        }
      }
    }
  }


  def isLinkExist(token: String, url: String) = {
    db withSession { implicit session =>
      // geting link which has a same url
      val existLink = (for {
        u <- Users if u.token === token
        l <- Links if l.user_id === u.id
      } yield l).filter(_.url === url)

      existLink.run.isEmpty match {
        case true => false
        case false => true
      }
    }
  }


  def addNewHashLink(token: String, url: String): Option[Link] = {
    val code = makeNewUrlCode(token, url)

    addNewUserLink(token, url, code)
  }


  def addNewUserLink(token: String, url: String, code: String): Option[Link] = {
    db withSession { implicit session =>
      val user: Option[User] = (for {
        u <- Users if u.token === token
      } yield u).run.headOption

      user match {
        case Some(user) => {
          // create link record
          val link = Link(None, user.id.get, url, code, is_user_link = false)
          Links insert link
          // get that link by another query because we have to get non empty folder primary key id
          val links: Seq[Link] = Links.findByUserId(user.id.get).run
          val link0: Option[Link] = links.find(p = _.code == code)
          link0

        }
        case None => {
          None
        }
      }
    }
  }

  def addNewFolder(token: String, title: String): Option[Folder] = {
    db withSession { implicit session =>
      val user: Option[User] = (for {
        u <- Users if u.token === token
      } yield u).run.headOption

      user match {
        case Some(user) => {
          // create folder record
          val folder = Folder(None, user.id.get, title)
          Folders insert folder
          // get that folder by another query because we have to get non empty folder primary key id
          val folders: Seq[Folder] = Folders.findByUserId(user.id.get).run
          val folder0 = folders.filter(_.title == title).headOption
          folder0
        }
        case None => {
          None
        }
      }
    }
  }


  def connectLinkWithFolder(folder_id: Int, link_id: Int) = {
    db withSession { implicit session =>
      FolderLinks insert FolderLink(folder_id, link_id)
    }

  }


  def addNewClicks(code: String, referer: String, remote_ip: String): Seq[Click] = {
    db withSession { implicit session =>
      val links: Seq[Link] = (for {
        l <- Links if l.code === code
      } yield l).run

      val clicks: Seq[Click] = for {
        l <- links
      } yield {
        val click = Click(None, l.id.get, DateTime.now, referer, InetString(remote_ip))
        Clicks insert click
        click
      }
      clicks
    }
  }


  def makeNewUrlCode(token: String,
                     url: String,
                     isUserLink: Boolean = false,
                     userLink: Option[String] = None): String = {
    db withSession { implicit session =>
      // get hash links which user has
      val links: Seq[Link] = (for {
        u <- Users if u.token === token
        l <- Links if l.user_id === u.id && l.is_user_link === false
      } yield l).sortBy(_.code.asc).run
      // get last link number
      val last: Option[Long] = links.length > 0 match {
        case true => {
          val l: Link = links.head
          val s: String = l.code
          decode(s).headOption
        }
        case false => None
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


  def getUrlCode(url: String): Option[String] = {
    db withSession { implicit session =>
      val links = (for {
        l <- Links if l.url === url
      } yield l).run

      links.isEmpty match {
        case true => None
        case false => Some(links.head.code)
      }
    }
  }


  def getUserId(token: String): Long = {
    hashids.decode(token).head
  }


  def getToken(user_id: Long): String = {
    hashids.encode(user_id)
  }


  def getLink(token: String, url: String): Option[Link] = {
    db withSession { implicit session =>
      // geting link which has a same url
      val q = for {
        u <- Users if u.token === token
        l <- Links if l.user_id === u.id && l.url === url
      } yield l

      val r =  q.run
      r.isEmpty match {
        case false => Some(r.head)
        case true => None
      }
    }
  }
}