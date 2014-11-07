import cc.evgeniy.shortened.ShortenedServerService
import org.specs2._
import org.specs2.mutable.Specification
import spray.http.MediaTypes._
import spray.testkit.Specs2RouteTest
import spray.http._
import StatusCodes._

class ShortenedServerSpec extends Specification with Specs2RouteTest with ShortenedServerService {

  def actorRefFactory = system // connect the DSL to the test ActorSystem

  val user_id   = "324"
  val secret    = "fdsfdsf"
  val token     = "92837498732"
  val url       = "http://www.google.com"
  val referer   = "wewe"
  val remote_ip = "10.10.0.22"
  val offset    = "0"
  val limit     = "25"
  val code      = "324324"

  "The service" should {

    "return a 'PONG' response for GET requests to /ping" in {
      Get("/ping") ~> apiRoute ~> check {
        responseAs[String] === "PONG"
      }
    }

    "POST to '/link' path with token, url, code [opt], folder_id [opt] params params" in {
      val body = s"""{\"token\": \"$token\", \"url\": \"$url\", \"code\": $code  }"""

      Post("/link",
        HttpEntity(`application/json`, body)) ~> apiRoute ~> check {
        //Check http status
        status === OK
      }
    }

    "POST to '/link/$code' path with referer, remote_ip params" in {
      Post("/link/876786876",
        HttpEntity(`application/json`,
          s"""{\"referer\": \"$referer\", \"remote_ip\": \"$remote_ip\"}""")) ~> apiRoute ~> check {
        //Check http status
        status === OK
      }
    }

    "GET to '/link/$code' path with token" in {
      Get(s"/link/876786876?token=$token") ~> apiRoute ~> check {
        //Check http status
        status === OK
      }
    }

    "GET to '/folder/$id' path with token, offset (opt = 0), limit (opt = const) params" in {
      Get(s"/link?token=$token&offset=$offset") ~> apiRoute ~> check {
        //Check http status
        status === OK
      }
    }

    "GET to '/link' path with token, offset (opt = 0), limit (opt = const) params" in {
      Get(s"/link?token=$token&offset=$offset") ~> apiRoute ~> check {
        //Check http status
        status === OK
      }
    }

    "GET to '/folder/$id' path with token, offset (opt = 0), limit (opt = const) params" in {
      Get(s"/link?token=$token") ~> apiRoute ~> check {
        //Check http status
        status === OK
      }
    }

    "GET to '/link/$code/clicks' path with token, offset, limit params" in {
      Get(s"/link?token=$token&offset=$offset&limit=$limit") ~> apiRoute ~> check {
        //Check http status
        status === OK
      }
    }


    /*
    "return a OK response for GET requests to the root path" in {
      Get("/") ~> apiRoute ~> check {
        //Check http status
        status === OK
        // content check
        responseAs[String] must contain("spray-can + spray-routing")
      }
    }


    "return a content-type for GET requests to the /simple_json" in {
      Get("/simple_json") ~> apiRoute ~> check {
        assert(contentType.mediaType.isApplication)

        //Check content type
        contentType.toString === "application/json; charset=UTF-8"
      }
    }
     */

  }
}
