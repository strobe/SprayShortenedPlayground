package cc.evgeniy.shortened

//import _root_.MyJsonProtocol._
import akka.actor._
import _root_.akka.io.IO
import akka.util.Timeout

import org.hashids.Hashids

import org.joda.time._
import org.joda.time.DateTime
import spray.can.Http
import spray.http.{StatusCode, HttpResponse, HttpEntity}
import spray.http.MediaTypes._
import spray.http.StatusCode._
import spray.httpx.SprayJsonSupport
import spray.json._
import spray.routing
import spray.routing.{Route, HttpService}
import spray.routing.authentication.BasicAuth
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.pattern.ask

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
  case class SourceLinkParameter(token: String, url: String, code: Option[Int], folderId: Option[Int])
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
            doLinkResponse(link)
          }
        } ~
          get {
            parameters('token.as[String], 'offset ? 0, 'limit ? "25") { (token, offset, limit) =>
              complete("not implemented")
            }
          }
      } ~
      pathPrefix(Segment) { code =>
        pathEnd {
          post {
            entity(as[ClickParameter]) { click =>
              complete(s"$click")
            } ~
              entity(as[SourceLinkParameter]) { link =>
                complete(s"$link")
              }
          } ~
            get {
              parameters('token.as[String]) { token =>
                complete("not implemented")
              }
            }
        } ~
          pathSuffix("clicks") {
            get {
              complete(code.toString)
            }
          }
      }
  } ~
  path("folder") {
    pathEnd {
      get {
        parameters('token.as[String]) { token =>
          complete("not implemented")
        }
      }
    } ~
    pathSuffix(LongNumber) { id =>
      get {
        parameters('token.as[String], 'offset ? 0, 'limit ? "25") { (token, offset, limit) =>
          complete("not implemented")
        }
      }
    }
  }

  ////////////// responses //////////////

  def doLinkResponse(link: SourceLinkParameter): Route = {
    isLinkExist(link.token, link.url) match {
      case true => {
        val l = getLink(link.token, link.url)

        completeLinkAsJson(l.url, l.code)
      }
      case false => {
        val user_id = getUserId(link.token)
        val code = makeNewUrlCode(link.token, link.url)

        addNewLink(link.token, link.url, code)
        completeLinkAsJson(link.url, code)
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


  def addNewLink(token: String, url: String, code: String) = {
    db withSession { implicit session =>
      val user: User = (for {
        u <- Users if u.token === token
      } yield u).run.head

      Links insert Link(None, user.id.get, url, code)
    }
  }


  def makeNewUrlCode(token: String, url: String): String = {
    db withSession { implicit session =>
      val userLinks: Seq[Link] = (for {
        u <- Users if u.token === token
        l <- Links if l.user_id === u.id
      } yield l).sortBy(_.code.asc).run

      val last: Option[Long] = userLinks.length > 0 match {
        case true => {
          val l: Link = userLinks.head
          val s: String = l.code
          decode(s).headOption
        }
        case false => None
      }
      last match {
        case Some(id) =>
          encode(id + 1L)
        case None =>
          encode(1L)
      }
    }
  }

  def getUrlCode(url: String) = {

  }

  def getUserId(token: String): Long = {
    hashids.decode(token).head
  }

  def getToken(user_id: Long): String = {
    hashids.encode(user_id)
  }

  def getLink(token: String, url: String): Link = {
    db withSession { implicit session =>
      // geting link which has a same url
      val q = for {
        u <- Users if u.token === token
        l <- Links if l.user_id === u.id && l.url === url
      } yield l

      val r =  q.run
        r.isEmpty match {
        case false => r.head
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