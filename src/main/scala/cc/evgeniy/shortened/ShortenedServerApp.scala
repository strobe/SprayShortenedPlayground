package cc.evgeniy.shortened

//import _root_.MyJsonProtocol._
import akka.actor._
import _root_.akka.io.IO
import akka.util.Timeout
import com.github.tminglei.slickpg.InetString
import org.apache.commons.validator.routines.UrlValidator

import org.hashids.Hashids

import spray.can.Http
import spray.http.{StatusCode, HttpResponse, HttpEntity}
import spray.http.StatusCodes._
import spray.http.MediaTypes._
import spray.httpx.SprayJsonSupport
import spray.json._
import spray.routing
import spray.routing.{Route, HttpService}
import spray.routing.authentication.BasicAuth
import org.joda.time.DateTime
import org.joda.time._
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.pattern.ask

import scala.slick.lifted.AppliedCompiledFunction

//import scala.slick.driver.PostgresDriver.simple._
import cc.evgeniy.shortened.ExtendedPostgresDriver.simple._
import com.github.tototoshi.slick.PostgresJodaSupport._

import scala.slick.lifted
import com.typesafe.config.ConfigFactory
//import com.github.kxbmap.configs._

/*
object Hello {
  def main(args: Array[String]): Unit = {
    println("Hello, world!")

    // loading configuration
    val config    = ConfigFactory.load()
    val user      = config.getString("urls-shortened.db.default.user")
    val password  = config.getString("urls-shortened.db.default.password")
    val driver    = config.getString("urls-shortened.db.default.driver")
    val url       = config.getString("urls-shortened.db.default.url")


    Database.forURL(url, driver = driver, user = user, password = password) withSession {
      implicit session =>

        // Insert some suppliers
//        Users insert User(None, "Acme, Inc.")
//        Users insert User(None, "Superior Coffee")
//        Users insert User(None, "The High Ground")
//
//        Folders insert Folder(None, 1, "Main")
//
//        Links insert Link(None, 2, "test", "test")
//        Links insert Link(None, 2, "test2", "test2")
//
//        FolderLinks insert FolderLink( 1, 1)

//        Clicks insert Click(None, 1, DateTime.now(), "some", )
    }
    val x = Database.forURL("jdbc:postgresql://localhost:5432/shortener_db", driver = "org.postgresql.Driver",
      user = "shortener_db", password = "carryx") withSession {
      implicit session =>
        val q1 = for(c <- Clicks) yield c
        q1 foreach println

    }
    println(x)

  }
}
*/

/**
 * Main entry point of app
 */
object ShortenedServerApp extends App {
  implicit val system = ActorSystem("spray-streamer-system")

  // server actor initialization
  val service = system.actorOf(Props[ShortenedServerActor], "spray-streamer-service")

  // http initialization
  IO(Http) ! Http.Bind(service, interface = "localhost", port = 8080)


}

object Dao {
  // loading configuration
  val config         = ConfigFactory.load()
  //
  val secret: String = config.getString("urls_service.secret")
  // db
  val user      = config.getString("urls_service.db_user")
  val password  = config.getString("urls_service.db_password")
  val driver    = config.getString("urls_service.db_driver")
  val url       = config.getString("urls_service.db_url")

  val db = Database.forURL(url, driver = driver, user = user, password = password)
//  db withSession {
//    implicit session =>
//      println("connected to DB")
//  }
}

/**
 * Server actor.
 * we don't implement our route structure directly in the service actor because
 * we want to be able to test it independently, without having to spin up an actor
 */
class ShortenedServerActor extends Actor with ShortenedServerService with ActorLogging {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing,
  // timeout handling or alternative handler registration
  def receive = runRoute(apiRoute) // route DSL
}


object RequestParams {
  // {"token": "12341", "url": "http://", "code": 21}
  case class SourceLinkParameter(token: String, url: String, code: Option[String], folder_id: Option[String])
  // {"referer": "12341", "remote_ip": "10.10.0.1"}
  case class ClickParameter(referer: String, remote_ip: String)

  object SourceLinkJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
    implicit val SourceLinkFormats = jsonFormat4(SourceLinkParameter)
    implicit val ClickFormats      = jsonFormat2(ClickParameter)
  }
}


trait UsersHashIDs{
  val hashids = Hashids("some salt", 0, "0123456789abcdef")
}

/**
 * Server trait.
 * this trait defines our service behavior independently from the service actor
 */
trait ShortenedServerService extends HttpService with UsersHashIDs with UrlCodec {
  import RequestParams._
  import RequestParams.SourceLinkJsonSupport._
  import Dao._


  // These implicit values allow us to use futures
  // in this trait.
  implicit def executionContext = actorRefFactory.dispatcher
  implicit val timeout = Timeout(2.seconds)

  //  Our worker Actor handles the work of the request.
//  val streamer = actorRefFactory.actorOf(Props[StreamerActor], "streamerActor")

  def uuid = java.util.UUID.randomUUID.toString

  val apiRoute =
    path("") {
      get {
        complete { index }
      }
    } ~
    path("ping") {
      get {
        complete("PONG")
      }
    } ~
    path("simple_json") {
      get {
        respondWithMediaType(`application/json`) {
          // Sray-json example https://github.com/spray/spray-json
          case class Color(name: String, red: Int, green: Int, blue: Int)
          object MyJsonProtocol extends DefaultJsonProtocol {
            implicit val colorFormat = jsonFormat4(Color)
          }
          import MyJsonProtocol._

          complete(Color("CadetBlue", 95, 158, 160).toJson.prettyPrint)
        }
      }
    } ~
    /// API ///
    path("token") {
      get { parameters('user_id.as[Int], 'secret.as[String]) { (user_id, secret) =>
        Dao.secret match { case Dao.secret =>
          doTokenResponse(user_id)
        }}}
    } ~
    pathPrefix("link") {
      pathEnd {
        post {
          entity(as[SourceLinkParameter]) { link =>
            val validator = new UrlValidator(Array("http", "https"))
            validator.isValid(link.url) match {
              case true => doPostLinkResponse(link)
              case false => complete(HttpResponse(BadRequest))
            }
          }
        } ~
        get {
          parameters('token.as[String], 'offset ? 0, 'limit ? 25) { (token, offset, limit) =>
            doGetLinksResponse(token, offset, limit)
          }
        }
      } ~
      pathPrefix(Segment) { code =>
        pathEnd {
          post {
            entity(as[ClickParameter]) { click =>
              val clicks = addNewClicks(code, click.referer, click.remote_ip)
              redirect(s"/link/${clicks.head}", PermanentRedirect)
            }
          } ~
          get {
            parameters('token.as[String]) { token =>
              doGetLinkResponse(token, code)
            }
          }
        } ~
        pathSuffix("clicks") {
          get {
            parameters('token.as[String], 'offset ? 0, 'limit ? 25) { (token, offset, limit) =>
              doGetClicksResponse(token, code, offset, limit)
            }
          }
        }
      }
  } ~
  pathPrefix("folder") {
    pathEnd {
      get {
        parameters('token.as[String]) { token =>
          doGetFolderResponse(token)
        }
      }
    } ~
    pathSuffix(IntNumber) { id =>
      get {
        parameters('token.as[String], 'offset ? 0, 'limit ? 25) { (token, offset, limit) =>
          doGetFolderResponseById(token, id, offset, limit)
        }
      }
    }
  }

  ////////////// responses //////////////

  def doGetFolderResponse(token: String): Route = {
    val folders: Seq[Folder] = db withSession { implicit session =>
      (for {
        u <- Users   if u.token === token
        f <- Folders if f.user_id === u.id
      } yield f).run
    }

    respondWithMediaType(`application/json`) {
      import FoldersJsonProtocol._
      val jsonFolders = for {f <- folders} yield f.toJson
      complete(JsObject("folders" -> jsonFolders.toJson))
    }
  }


  def doGetFolderResponseById(token: String, id: Int, offset: Int, limit: Int): Route = {
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
      respondWithMediaType(`application/json`) {
        import LinksJsonProtocol._
        val jsonLinks = for {l <- links} yield l.toJson
        complete(JsObject("links" -> jsonLinks.toJson))
      }
    }
    else {
      complete(HttpResponse(NotFound))
    }
  }


  def doGetClicksResponse(token: String, code: String, offset: Int, limit: Int): Route = {
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

    respondWithMediaType(`application/json`) {
      import ClicksJsonProtocol._
      val jsonClicks = for {c <- clicks} yield c.toJson
      complete(JsObject("clicks" -> jsonClicks.toJson))
    }
  }


  def doGetLinkResponse(token: String, code: String): Route = {
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

          respondWithMediaType(`application/json`) {
            folder match {
              case Some(f) => {
                complete(JsObject("link" -> JsObject(
                  "url" -> JsString(l.url),
                  "code" -> JsString(l.code)),
                  "clicks" -> JsNumber(clicks.length),
                  "folder" -> JsString(f.title)).prettyPrint)
              }
              case None => {
                complete(JsObject("link" -> JsObject(
                  "url" -> JsString(l.url),
                  "code" -> JsString(l.code)),
                  "clicks" -> JsNumber(clicks.length)).prettyPrint)
              }
            }
          }
        }
        case None => complete(HttpResponse(NotFound))
      }


    }
  }


  def doGetLinksResponse(token: String, offset: Int, limit: Int): Route = {
    val offset0 = if (offset > 0) offset else 0
    val limit0 = if (limit > 0) limit else 25

    val links = db withSession { implicit session =>
      val existLinks: Seq[Link] = (for {
        u <- Users if u.token === token
        l <- Links if l.user_id === u.id
      } yield l).run
      existLinks.drop(offset0).take(limit0)
    }

    respondWithMediaType(`application/json`) {
      import LinksJsonProtocol._
      val jsonLinks = for {l <- links} yield l.toJson
      complete(JsObject("links" -> jsonLinks.toJson))
    }
  }


  def doPostLinkResponse(link: SourceLinkParameter): Route = {
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
        case e: Exception => complete(HttpResponse(UnprocessableEntity,
          s"new folder link creation failed:\n ${e.toString}"))
      }
    }
    // end //

    isLinkExist(link.token, link.url) match {
      case true => {
        getLink(link.token, link.url) match {
          case Some(l) => completeLinkAsJson (l.url, l.code)
          case None    => complete(HttpResponse(NotFound))
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
          case None => complete(HttpResponse(UnprocessableEntity))
        }
      }
    }
  }


  def doTokenResponse(user_id: Int): routing.Route = {
    val token: String = hashids.encode(user_id)

    db withSession { implicit session =>
      val query = TableQuery[Users].filter(_.token === token).run
      query.isEmpty match {
        case false => {
          respondWithMediaType(`application/json`) {
            complete(JsObject("token" -> JsString(query.seq.head.token)).prettyPrint)
          }
        }
        case true => {
          Users insert User(None, token)
          respondWithMediaType(`application/json`) {
            complete(JsObject("token" -> JsString(token)).prettyPrint)
          }
        }
      }
    }
  }


  ////////////// helpers //////////////

  def completeLinkAsJson(url: String, code: String) = {
    respondWithMediaType(`application/json`) {
      complete(JsObject("link" -> JsObject(
        "url" -> JsString(url),
        "code" -> JsString(code)
      )).prettyPrint)
    }
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


  lazy val index =
    HttpEntity(`text/html`,
      <html>
        <body>
          <h2><i>test</i>!</h2>
          <p>Defined resources:</p>
          <ul>
            <li><div>just ping  <a href="/ping">/ping</a></div></li>
            <li><div>json test  <a href="/simple_json">/simple_json</a></div></li>
          </ul>
        </body>
      </html>.toString()
    )

}