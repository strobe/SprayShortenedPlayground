import cc.evgeniy.shortener.ShortenedServerService
import org.specs2._
import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.http._
import StatusCodes._

class HelloSpec extends Specification with Specs2RouteTest with ShortenedServerService {

  def actorRefFactory = system // connect the DSL to the test ActorSystem

  "The service" should {

    "return a OK response for GET requests to the root path" in {
      Get("/") ~> apiRoute ~> check {
        //Check http status
        status === OK
        // content check
        responseAs[String] must contain("spray-can + spray-routing")
      }
    }

    "return a 'PONG' response for GET requests to /ping" in {
      Get("/ping") ~> apiRoute ~> check {
        responseAs[String] === "PONG"
      }
    }

    "return a content-type for GET requests to the /simple_json" in {
      Get("/simple_json") ~> apiRoute ~> check {
        assert(contentType.mediaType.isApplication)

        //Check content type
        contentType.toString === "application/json; charset=UTF-8"
      }
    }

  }
}
