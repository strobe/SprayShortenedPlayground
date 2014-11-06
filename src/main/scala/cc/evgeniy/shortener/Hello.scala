package cc.evgeniy.shortener

import akka.actor._
import _root_.akka.io.IO
import akka.util.Timeout
import org.joda.time._
import org.joda.time.DateTime
import spray.can.Http
import spray.http.HttpEntity
import spray.http.MediaTypes._
import spray.json.DefaultJsonProtocol
import spray.routing.HttpService
import spray.json._
import scala.concurrent.duration._
import akka.pattern.ask

//import scala.slick.driver.PostgresDriver.simple._
import cc.evgeniy.shortener.ExtendedPostgresDriver.simple._
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
    val user      = config.getString("urls-shortener.db.default.user")
    val password  = config.getString("urls-shortener.db.default.password")
    val driver    = config.getString("urls-shortener.db.default.driver")
    val url       = config.getString("urls-shortener.db.default.url")


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
object AkkaSprayScalaExample extends App {
  implicit val system = ActorSystem("spray-streamer-system")

  // server actor initialization
  val service = system.actorOf(Props[ShortenedServerActor], "spray-streamer-service")

  // http initialization
  IO(Http) ! Http.Bind(service, interface = "localhost", port = 8050)
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

/**
 * Server trait.
 * this trait defines our service behavior independently from the service actor
 */
trait ShortenedServerService extends HttpService {

  // These implicit values allow us to use futures
  // in this trait.
  implicit def executionContext = actorRefFactory.dispatcher
  implicit val timeout = Timeout(2.seconds)

  //  Our worker Actor handles the work of the request.
//  val streamer = actorRefFactory.actorOf(Props[StreamerActor], "streamerActor")

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
      }

  ////////////// helpers //////////////

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