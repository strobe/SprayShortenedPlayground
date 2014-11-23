package cc.evgeniy.shortened

import akka.actor._
import _root_.akka.io.IO
import akka.util.Timeout
import org.apache.commons.validator.routines.UrlValidator
import org.hashids.Hashids
import spray.can.Http
import spray.http.{HttpResponse, HttpEntity}
import spray.http.StatusCodes._
import spray.http.MediaTypes._
import spray.httpx.SprayJsonSupport
import spray.json._
import spray.routing.HttpService
import scala.concurrent.duration._
import akka.pattern.ask


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
trait ShortenedServerService extends HttpService with UsersHashIDs with UrlCodec {
  import RequestParams._
  import RequestParams.SourceLinkJsonSupport._


  // These implicit values allow us to use futures
  // in this trait.
  implicit def executionContext = actorRefFactory.dispatcher
  implicit val timeout = Timeout(2.seconds)

  //  Our worker Actor handles the work of the request.
  val shortened = actorRefFactory.actorOf(Props[ShortenedControllerActor],
                                          "shortened-controller")

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
    path("token") {
      get { parameters('user_id.as[Int], 'secret.as[String]) { (user_id, secret) =>
         if (Dao.secret == secret) {
           val response = (shortened ? AskTokenResponse(user_id))
             .mapTo[HttpResponse]
             .recover { case e => HttpResponse(entity = HttpEntity(s"error: ${e}"))}
           complete(response)
         }
         else {
          complete(HttpResponse(Unauthorized))
         }
      }}
    } ~
    pathPrefix("link") {
      pathEnd {
        post {
          entity(as[SourceLinkParameter]) { link =>
            val validator = new UrlValidator(Array("http", "https"))
            validator.isValid(link.url) match {
              case true => {
                val response = (shortened ? AskPostLinkResponse(link))
                  .mapTo[HttpResponse]
                  .recover { case e => HttpResponse(entity = HttpEntity(s"error: ${e}"))}
                complete(response)
              }
              case false => complete(HttpResponse(BadRequest))
            }
          }
        } ~
        get {
          parameters('token.as[String], 'offset ? 0, 'limit ? 25) { (token, offset, limit) =>
            val response = (shortened ? AskGetLinksResponse(token, offset, limit))
              .mapTo[HttpResponse]
              .recover { case e => HttpResponse(entity = HttpEntity(s"error: ${e}")) }
            complete(response)
          }
        }
      } ~
      pathPrefix(Segment) { code =>
        pathEnd {
          post {
            entity(as[ClickParameter]) { click =>
              val c = (shortened ? AskAddNewClicks(code, click))
                .mapTo[Click]
                .recover { case e => HttpResponse(entity = HttpEntity(s"error: ${e}")) }
              redirect(s"/link/$c", PermanentRedirect)
            }
          } ~
          get {
            parameters('token.as[String]) { token =>
              val response = (shortened ? AskGetLinkResponse(token, code))
                .mapTo[HttpResponse]
                .recover { case e => HttpResponse(entity = HttpEntity(s"error: ${e}")) }
              complete(response)
            }
          }
        } ~
        pathSuffix("clicks") {
          get {
            parameters('token.as[String], 'offset ? 0, 'limit ? 25) { (token, offset, limit) =>
              val response = (shortened ? AskGetClicksResponse(token, code, offset, limit))
                .mapTo[HttpResponse]
                .recover { case e => HttpResponse(entity = HttpEntity(s"error: ${e}")) }
              complete(response)
            }
          }
        }
      }
  } ~
  pathPrefix("folder") {
    pathEnd {
      get {
        parameters('token.as[String]) { token =>
          val response = (shortened ? AskGetFolderResponse(token))
            .mapTo[HttpResponse]
            .recover { case e => HttpResponse(entity = HttpEntity(s"error: ${e}")) }
          complete(response)
        }
      }
    } ~
    pathSuffix(IntNumber) { id =>
      get {
        parameters('token.as[String], 'offset ? 0, 'limit ? 25) { (token, offset, limit) =>
          val response = (shortened ? AskGetFolderResponseById(token, id, offset, limit))
            .mapTo[HttpResponse]
            .recover { case e => HttpResponse(entity = HttpEntity(s"error: ${e}")) }
            complete(response)
        }
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
          </ul>
        </body>
      </html>.toString()
    )

}


object RequestParams {
  case class SourceLinkParameter(token: String, url: String, code: Option[String], folder_id: Option[String])
  case class ClickParameter(referer: String, remote_ip: String)

  object SourceLinkJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
    implicit val SourceLinkFormats = jsonFormat4(SourceLinkParameter)
    implicit val ClickFormats      = jsonFormat2(ClickParameter)
  }
}


trait UsersHashIDs{
  val hashids = Hashids("some salt", 0, "0123456789abcdef")
}
