package xyz.volgar1x.showhost.persist

import io.getquill.MappedEncoding
import xyz.volgar1x.showhost.ULID

package object jdbc:
  implicit val ulidDecoding: MappedEncoding[String, ULID] = MappedEncoding(ULID(_))
  implicit val ulidEncoding: MappedEncoding[ULID, String] = MappedEncoding(_.value)
