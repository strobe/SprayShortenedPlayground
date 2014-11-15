package cc.evgeniy.shortened

import org.joda.time._
import org.joda.time.DateTime
import spray.json._

//import scala.slick.driver.PostgresDriver.simple._
import cc.evgeniy.shortened.ExtendedPostgresDriver.simple._
import com.github.tototoshi.slick.PostgresJodaSupport._
import com.github.tminglei.slickpg._


/// User ///
// mapped User type
case class User(id: Option[Long], token: String)

// Definition of the 'Users' table
class Users(tag: Tag) extends Table[User](tag, "Users") {
  def id    = column[Long]("user_id", O.PrimaryKey, O.AutoInc) // This is the primary key column
  def token = column[String]("token")

  // Every table needs a * projection with the same type as the table's type parameter
  def * = (id.?, token) <> ((User.apply _).tupled, User.unapply)
}

// TableQuery object for adding additional functionality
object Users extends TableQuery(new Users(_)) {
  val findByToken = this.findBy(_.token)
}
/// end ///


/// Folder ///
// mapped Folder type
case class Folder(id: Option[Int], user_id: Long, title: String)

// Definition of the 'Users' table
class Folders(tag: Tag) extends Table[Folder](tag, "Folders") {
  def id      = column[Int]("folder_id", O.PrimaryKey, O.AutoInc) // This is the primary key column
  def user_id = column[Long]("user_id", O.NotNull) // foreign key
  def title   = column[String]("title")

  // Every table needs a * projection with the same type as the table's type parameter
  def * = (id.?, user_id, title) <> ((Folder.apply _).tupled, Folder.unapply)

  // foreign keys
  def userFK = foreignKey("USER_FK_CONS", user_id, Users)(_.id)
}

// TableQuery object for adding additional functionality
object Folders extends TableQuery(new Folders(_)) {
  val findById = this.findBy(_.id)
  val findByTitle = this.findBy(_.title)
  val findByUserId = this.findBy(_.user_id)
}
/// end ///


/// Link ///
// mapped Link type
case class Link(id: Option[Int], user_id: Long, url: String, code: String, is_user_link: Boolean)

// Json writer
object LinksJsonProtocol extends DefaultJsonProtocol {
  implicit object linksFormat extends RootJsonFormat[Link] {

    def write(l: Link) =
      JsObject("url" -> JsString(l.url), "code" -> JsString(l.code))

    def read(value: JsValue) = value match {
      case _ => deserializationError("Link deserialization is not supported")
    }
  }
}

// Definition of the 'Users' table
class Links(tag: Tag) extends Table[Link](tag, "Links") {
  def id           = column[Int]("link_id", O.PrimaryKey, O.AutoInc) // This is the primary key column
  def user_id      = column[Long]("user_id", O.NotNull) // foreign key
  def url          = column[String]("url")
  def code         = column[String]("code")
  def is_user_link = column[Boolean]("is_user_link")

  // Every table needs a * projection with the same type as the table's type parameter
  def * = (id.?, user_id, url, code, is_user_link) <> ((Link.apply _).tupled, Link.unapply)

  // foreign keys
  def userFK = foreignKey("USER_FK_CONS", user_id, Users)(_.id)
}

// TableQuery object for adding additional functionality
object Links extends TableQuery(new Links(_)) {
  val findByCode = this.findBy(_.code)
  val findByUrl = this.findBy(_.url)
  val findByUserId = this.findBy(_.user_id)
}
/// end ///


/// Click ///
// mapped User type
case class Click(id: Option[Int], link_id: Int, date: DateTime, referer: String, remote_ip: InetString)

// Json writer
object ClicksJsonProtocol extends DefaultJsonProtocol {
  implicit object clicksFormat extends RootJsonFormat[Click] {

    def write(l: Click) =
      JsObject("date"      -> JsString(l.date.toDateTimeISO.toString),
               "refer"     -> JsString(l.referer),
               "remote_ip" -> JsString(l.remote_ip.address.toString))

    def read(value: JsValue) = value match {
      case _ => deserializationError("Click deserialization is not supported")
    }
  }
}

// Definition of the 'Users' table
class Clicks(tag: Tag) extends Table[Click](tag, "Clicks") {
  def id      = column[Int]("click_id", O.PrimaryKey, O.AutoInc) // This is the primary key column
  def link_id = column[Int]("link_id", O.NotNull) // foreign key
  def date    = column[DateTime]("date")
  def referer = column[String]("referer")
  def remote_ip = column[InetString]("remote_ip")

  // Every table needs a * projection with the same type as the table's type parameter
  def * = (id.?, link_id, date, referer, remote_ip) <> ((Click.apply _).tupled, Click.unapply)

  // foreign keys
  def linkFK = foreignKey("LINK_FK_CONS", link_id, Links)(_.id)
}

// TableQuery object for adding additional functionality
object Clicks extends TableQuery(new Clicks(_)) {

  val findByLinkId  = this.findBy(_.link_id)
  val findByDate    = this.findBy(_.date)
  val findByReferer = this.findBy(_.referer)
}
/// end ///


/// FolderLinks ///
// mapped FolderLinks type
case class FolderLink(folder_id: Int, link_id: Int)

// Json writer
object FoldersJsonProtocol extends DefaultJsonProtocol {
  implicit object foldersFormat extends RootJsonFormat[Folder] {

    def write(f: Folder) =
      JsObject("id"      -> JsString(f.id.toString),
               "title"   -> JsString(f.title))

    def read(value: JsValue) = value match {
      case _ => deserializationError("folder deserialization is not supported")
    }
  }
}

// Definition of the 'FolderLinks' table
class FolderLinks(tag: Tag) extends Table[FolderLink](tag, "FolderLinks") {
  def folder_id  = column[Int]("folder_id", O.PrimaryKey, O.NotNull) // This is the primary key column
  def link_id    = column[Int]("link_id", O.NotNull)

  // Every table needs a * projection with the same type as the table's type parameter
  def * = (folder_id, link_id) <> ((FolderLink.apply _).tupled, FolderLink.unapply)

  // foreign keys
  def folderFK = foreignKey("FOLDER_FK_CONS", folder_id, Folders)(_.id)
  def linkFK   = foreignKey("LINK_FK_CONS", link_id, Links)(_.id)
}

// TableQuery object for adding additional functionality
object FolderLinks extends TableQuery(new FolderLinks(_)) {
  val findByFolderId = this.findBy(_.folder_id)
  val findByLinkId   = this.findBy(_.link_id)
}
/// end ///
