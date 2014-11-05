package cc.evgeniy.shortener

import org.joda.time._
import org.joda.time.DateTime
//import scala.slick.driver.PostgresDriver.simple._
import cc.evgeniy.shortener.ExtendedPostgresDriver.simple._
import com.github.tototoshi.slick.PostgresJodaSupport._

import scala.slick.lifted
import com.typesafe.config.ConfigFactory
//import com.github.kxbmap.configs._

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
