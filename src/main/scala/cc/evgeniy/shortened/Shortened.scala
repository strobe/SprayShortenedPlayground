package cc.evgeniy.shortened

import org.hashids.Hashids

trait UrlCodec {

  private val alphabet = ((0 to 9) ++ ('A' to 'Z') ++ ('a' to 'z') ++ "-_~[]()*=").mkString
  private val hashids = Hashids("$#*yuh@#jkj*8983-salt", 0 , alphabet)

  def encode(id: String) = {
    hashids.decode(id)
  }

  def encode(id: Long) = {
    hashids.encode(id)
  }

}
