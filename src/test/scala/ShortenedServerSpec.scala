import java.util.Date

import cc.evgeniy.shortened.ShortenedServerService
import com.typesafe.config.ConfigFactory
import org.specs2._
import org.specs2.mutable.Specification
import spray.http.MediaTypes._
import spray.testkit.Specs2RouteTest
import spray.http._
import StatusCodes._
import spray.json._
import DefaultJsonProtocol._
import util.Random._
import org.joda.time._
import org.joda.time.DateTime

import org.specs2.specification.{Fragments, Step}


trait BeforeAllAfterAll extends Specification {
  // see http://bit.ly/11I9kFM (specs2 User Guide)
  override def map(fragments: => Fragments) =
    Step(beforeAll) ^ fragments ^ Step(afterAll)

  protected def beforeAll()
  protected def afterAll()
}


class ShortenedServerSpec extends Specification
    with Specs2RouteTest with ShortenedServerService with BeforeAllAfterAll {

  def actorRefFactory = system // connect the DSL to the test ActorSystem

  val user_id   = 5646547L
  val url       = "http://www.google.com"
  val referer   = "referer"
  val remote_ip = "10.10.0.22"
  val offset    = "0"
  val limit     = "25"

  val token: String = hashids.encode(user_id)

  // loading configuration
  val config         = ConfigFactory.load()
  val secret: String = config.getString("urls_service.secret")

  /**
   *  this request will create user on server which is needed to exist for some
   *  of requests (for case where database is empty)
   */
  def beforeAll(): Unit = {
    def createFirstUser = {
      Get(s"/token?user_id=$user_id&secret=$secret") ~> apiRoute
    }
    createFirstUser
  }

  def afterAll() = {}

  /**
   * Specification
   */
  override def is =
  s2"""
    this is Shortened Server specification
      GET ping                                                                           $t1_ping
      return a '$token' in Json response for GET requests to /token"                 $t2_token
      POST to '/link' path with token, url params"                                   $t3_post_link0
      POST to '/link' path with token, url, code [opt] params                        $t4_post_link1
      POST to '/link' path with token, url, code [opt] and folder [opt] params       $t5_post_link2
      POST to '/link/code' path with referer, remote_ip params in                    $t6_post_link_code0
      GET to '/link/code' path with token                                            $t7_get_link_code
      GET to '/link' path with token, offset (opt = 0), limit (opt = const) params   $t8_get_link0
      GET to '/folder' path with token, offset (opt = 0), limit (opt = const) params $t9_get_folder
      GET to '/folder/:id                                                            $t10_get_folder_id
      "GET to '/link/code/clicks' path with token, offset, limit params" in          $t11_get_link_code_clicks
  """

  def t1_ping = {
    Get("/ping") ~> apiRoute ~> check {
      responseAs[String] === "PONG"
    }
  }


  def t2_token = {
    Get(s"/token?user_id=$user_id&secret=$secret") ~> apiRoute ~> check {
      //Check http status
      status === OK
      contentType.toString must contain("application/json")
      responseAs[String] must contain(token.toString)
    }
  }


  def t3_post_link0 = {
    val send_body = s"""{\"token\": \"$token\", \"url\": \"$url\"  }"""

    Post("/link",
      HttpEntity(`application/json`, send_body)) ~> apiRoute ~> check {
      //Check http status
      status === OK
      contentType.toString must contain("application/json")
      responseAs[String] must contain(url)
    }
  }


  def t4_post_link1 = {
    val body = s"""{\"token\": \"$token\", \"url\": \"$url\", \"code\": "usercode@" }"""

    Post("/link",
      HttpEntity(`application/json`, body)) ~> apiRoute ~> check {
      //Check http status
      status === OK
      contentType.toString must contain("application/json")
      responseAs[String] must contain(url)
    }
  }


  def t5_post_link2 = {
    val r = new scala.util.Random(DateTime.now().getMillis())
    val url0 = s"https://www.akka.io/${r.nextInt().toString}"
    val folder_id = "some_folder"
    val body = s"""{\"token\": \"$token\", \"url\": \"$url0\", \"code\": "usercode@${r.nextInt.toString}", \"folder_id\": \"$folder_id\" }"""

    Post("/link",
      HttpEntity(`application/json`, body)) ~> apiRoute ~> check {
      //Check http status
      status === OK
      contentType.toString must contain("application/json")
      responseAs[String] must contain(url0)
    }
  }


  def t6_post_link_code0 = {
    val send_body = s"""{\"token\": \"$token\", \"url\": \"$url\"  }"""
    var code = ""

    // getting the code of link
    Post("/link",
      HttpEntity(`application/json`, send_body)) ~> apiRoute ~> check {
      val jsonAst = body.data.asString.parseJson
      code = jsonAst.asJsObject.fields("link").asJsObject.fields("code") match {
        case JsString(code) => code
      }
    }

    // actual test case
    Post(s"/link/$code",
      HttpEntity(`application/json`,
        s"""{\"referer\": \"$referer\", \"remote_ip\": \"$remote_ip\"}""")) ~> apiRoute ~> check {
      println(body.toString)
      println(headers.toString)
      //Check http status
      status === PermanentRedirect
    }
  }


  def t7_get_link_code = {
    val send_body = s"""{\"token\": \"$token\", \"url\": \"$url\"  }"""
    var code = ""

    // getting the code of link
    Post("/link",
      HttpEntity(`application/json`, send_body)) ~> apiRoute ~> check {
      val jsonAst = body.data.asString.parseJson
      code = jsonAst.asJsObject.fields("link").asJsObject.fields("code") match {
        case JsString(code) => code
      }
    }

    Get(s"/link/$code?token=$token") ~> apiRoute ~> check {
      //Check http status
      status === OK
      contentType.toString must contain("application/json")
      responseAs[String] must contain("link")
      responseAs[String] must contain("url")
      responseAs[String] must contain("code")
      responseAs[String] must contain("clicks")
    }
  }


  def t8_get_link0 = {
    Get(s"/link?token=$token&limit=$limit&offset=$offset") ~> apiRoute ~> check {
      //Check http status
      status === OK
      contentType.toString must contain("application/json")
      responseAs[String] must contain("links")
    }
  }


  def t9_get_folder = {
    Get(s"/folder?token=$token") ~> apiRoute ~> check {
      //Check http status
      status === OK
      contentType.toString must contain("application/json")
      responseAs[String] must contain("folders")
    }
  }


  // TODO: is a placeholder
  def t10_get_folder_id = {
    Get(s"/folder/${1}?token=$token") ~> apiRoute ~> check {
      //Check http status
      status === OK
      contentType.toString must contain("application/json")
      responseAs[String] must contain("links")
      responseAs[String] must contain("url")
      responseAs[String] must contain("code")
    }
  }


  def t11_get_link_code_clicks = {
    val send_body = s"""{\"token\": \"$token\", \"url\": \"$url\"  }"""
    var code = ""

    // getting the code of link
    Post("/link",
      HttpEntity(`application/json`, send_body)) ~> apiRoute ~> check {
      val jsonAst = body.data.asString.parseJson
      code = jsonAst.asJsObject.fields("link").asJsObject.fields("code") match {
        case JsString(code) => code
      }
    }

    Get(s"/link/$code/clicks?token=$token&offset=$offset&limit=$limit") ~> apiRoute ~> check {
      //Check http status
      status === OK
      contentType.toString must contain("application/json")
      responseAs[String] must contain("clicks")
    }
  }
}
