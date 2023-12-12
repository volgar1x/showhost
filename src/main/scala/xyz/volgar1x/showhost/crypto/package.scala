package xyz.volgar1x.showhost

import com.google.common.io.BaseEncoding

import java.nio.ByteBuffer
import java.time.{LocalDateTime, ZoneId, ZonedDateTime}

package object crypto:
  extension (byteBuffer: ByteBuffer)
    inline def toArray: Array[Byte] =
      val array = Array.ofDim[Byte](byteBuffer.limit())
      byteBuffer.get(array)
      array

  extension (dt: LocalDateTime)
    inline def +(duration: zio.Duration): LocalDateTime = dt.plus(duration)
    inline def withZone(zone: ZoneId): ZonedDateTime    = ZonedDateTime.of(dt, zone)

  extension (bytes: Array[Byte])
    inline def b64encode: String = BaseEncoding.base64().encode(bytes)
    inline def b16encode: String = BaseEncoding.base16().encode(bytes)
  extension (string: String)
    inline def b64decode: Array[Byte] = BaseEncoding.base64().decode(string)
    inline def b16decode: Array[Byte] = BaseEncoding.base16().decode(string)
