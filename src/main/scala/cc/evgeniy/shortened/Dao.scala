package cc.evgeniy.shortened

import cc.evgeniy.shortened.RequestParams.ClickParameter
import com.github.tminglei.slickpg.InetString
import com.typesafe.config.ConfigFactory
import cc.evgeniy.shortened.ExtendedPostgresDriver.simple._
import org.joda.time.DateTime

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

  ////////////// geting data //////////////
  
  def getUsersByToken(token: String): Seq[User] = {
    db withSession { implicit session =>
      (for { u <- Users if u.token === token} yield u).run
    }
  }


  def getLastLinkCode(token: String): Option[String] = {
    db withSession { implicit session =>
      // get hash links which user has
      val links: Seq[Link] = (for {
        u <- Users if u.token === token
        l <- Links if l.user_id === u.id && l.is_user_link === false
      } yield l).sortBy(_.code.asc).run
      // get last link number
      val code: Option[String] = links.length > 0 match {
        case true => {
          val l: Link = links.head
          val s: Option[String] = Some(l.code)
          s
        }
        case false => None
      }
      code
    }
  }


  def getClicksByLimitAndOffset(token: String, limit0: Int, offset0: Int): Seq[Click] = {
    db withSession { implicit session =>
      val existClicks: Seq[Click] = (for {
        u <- Users if u.token === token
        l <- Links if l.user_id === u.id
        c <- Clicks if c.link_id === l.id
      } yield c).run

      existClicks.drop(offset0).take(limit0)
    }
  }


  def getClicksByLinkId(id: Int): Seq[Click] = {
    db withSession { implicit session =>
      Clicks.findByLinkId(id).run
    }
  }


  def getFoldersLinksByLimitAndOffset(token: String, id: Int, limit0: Int, offset0: Int): Seq[Link] = {
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
    links
  }


  def getLinksByLimitAndOffset(token: String, offset: Int, limit: Int): Seq[Link] = {
    val links = db withSession { implicit session =>
      val existLinks: Seq[Link] = (for {
        u <- Users if u.token === token
        l <- Links if l.user_id === u.id
      } yield l).run
      existLinks.drop(offset).take(limit)
    }
    links
  }


  def getFolderLinkByLinkId(id: Int): Option[FolderLink] = {
    db withSession { implicit session =>
      FolderLinks.findByLinkId(id).run.headOption
    }
  }


  def getFoldersByUserToken(token: String): Seq[Folder] = {
    val folders: Seq[Folder] = db withSession { implicit session =>
      (for {
        u <- Users if u.token === token
        f <- Folders if f.user_id === u.id
      } yield f).run
    }
    folders
  }


  def getFolderById(id: Int): Option[Folder] = {
    db withSession { implicit session =>
      Folders.findById(id).run.headOption
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


  def getLinkByUrl(token: String, url: String): Option[Link] = {
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


  def getLinkByCode(token: String, code: String): Option[Link] = {
    val link: Option[Link] = db withSession { implicit session =>
      (for {
        u <- Users if u.token === token
        l <- Links if l.user_id === u.id && l.code === code
      } yield l).run.headOption
    }
    link
  }

  
  ////////////// is it? //////////////

  
  def isUserTokenCorrect(token: String): Boolean = {
    db withSession { implicit session =>
      val query: Seq[User] = TableQuery[Users].filter(_.token === token).run
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


  def isLinkExist(token: String, url: String): Boolean = {
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

  
  ////////////// adding //////////////

  
  def addUser(token: String) = {
    db withSession { implicit session =>
      Users insert User(None, token)
    }
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


  def addNewClicks(code: String, click: ClickParameter): Click = {
    val clicks = addNewClicks(code, click.referer, click.remote_ip)
    clicks.head
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


  ////////////// connecting //////////////


  def connectLinkWithFolder(folder_id: Int, link_id: Int) = {
    db withSession { implicit session =>
      FolderLinks insert FolderLink(folder_id, link_id)
    }
  }

}
