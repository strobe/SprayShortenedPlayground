package cc.evgeniy.shortened

import com.typesafe.config.ConfigFactory
import cc.evgeniy.shortened.ExtendedPostgresDriver.simple._

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
}
