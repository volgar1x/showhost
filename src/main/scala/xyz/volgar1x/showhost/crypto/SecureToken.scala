package xyz.volgar1x.showhost.crypto

import zio.*
import zio.json.ast.Json
import zio.json.{JsonDecoder, JsonEncoder}

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.{ByteBuffer, CharBuffer}
import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import javax.crypto.Cipher
import javax.crypto.spec.{GCMParameterSpec, SecretKeySpec}
import scala.util.Try

sealed trait Padding[F[_]]:
  def pad(bytes: Array[Byte]): Array[Byte]
  def unpad(bytes: Array[Byte]): F[Array[Byte]]

class Pkcs7(val blockSize: Int) extends Padding[Option]:
  def pad(bytes: Array[Byte]): Array[Byte] =
    val newBytes = Array.ofDim[Byte](bytes.length + blockSize)
    bytes.copyToArray(newBytes)
    val padding = bytes.length % blockSize match
      case 0       => blockSize
      case padding => blockSize
    (bytes.length until newBytes.length).foreach { idx =>
      newBytes.update(idx, padding.asInstanceOf[Byte])
    }
    newBytes

  def unpad(bytes: Array[Byte]): Option[Array[Byte]] =
    val padding = bytes.last
    val end     = bytes.length - padding
    if bytes.iterator.drop(end).forall(_ == padding)
    then Some(bytes.slice(0, end))
    else None

case class SecureToken(encoded: String)

object SecureToken:
  enum CipherType(val name: String, val fullName: String, val padding: Padding[Option], val blockSize: Int, val tagLen: Int = 0):
    def newCipher: Cipher = Cipher.getInstance(fullName)

    case AES_256_GCM extends CipherType("AES", "AES_256/GCM/NoPadding", Pkcs7(32), 32, 128)

  def randomKey(cipherType: CipherType = CipherType.AES_256_GCM): UIO[Array[Byte]] =
    Random.nextBytes(cipherType.blockSize).map(_.toArray)

  def encrypt[T: JsonEncoder](
      data: T,
      key: Array[Byte],
      expires: Duration,
      iv: Option[Array[Byte]] = None,
      cipherType: CipherType = CipherType.AES_256_GCM,
      charset: Charset = UTF_8
  )(using timeZone: ZoneId): UIO[(SecureToken, ZonedDateTime)] =
    for
      iv <- iv.fold(Random.nextBytes(cipherType.blockSize).map(_.toArray))(ZIO.succeed(_))
      dt <- Clock.localDateTime
    yield
      val expiration = (dt + expires).withZone(timeZone)
      val result     = encrypt2(data, key, iv, expiration, cipherType, charset)
      (SecureToken(result.b64encode), expiration)

  def decrypt[T: JsonDecoder](
      data: SecureToken,
      keyData: Array[Byte],
      cipherType: CipherType = CipherType.AES_256_GCM,
      charset: Charset = UTF_8
  )(using timeZone: ZoneId): IO[Option[Nothing], T] =
    for
      result <- ZIO.fromOption(decrypt2(data.encoded.b64decode, keyData, cipherType, charset))
      dt     <- Clock.localDateTime
      _      <- ZIO.cond(dt.withZone(timeZone).isBefore(result._2), {}, None)
    yield result._1

  def encrypt2[T: JsonEncoder](
      data: T,
      keyData: Array[Byte],
      ivData: Array[Byte],
      expiration: ZonedDateTime,
      cipherType: CipherType = CipherType.AES_256_GCM,
      charset: Charset = UTF_8
  ): Array[Byte] =
    val payload = cipherType.padding.pad(
      charset
        .encode(
          CharBuffer.wrap(
            Json.encoder.encodeJson(
              Json.Obj(
                "data" -> JsonEncoder[T].toJsonAST(data).fold(error => throw new IllegalArgumentException(error), identity),
                "exp"  -> Json.Str(expiration.format(DateTimeFormatter.ISO_ZONED_DATE_TIME))
              )
            )
          )
        )
        .toArray
    )
    val cipher = cipherType.newCipher
    val key    = SecretKeySpec(keyData, cipherType.name)
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(cipherType.tagLen, ivData))
    ivData ++ cipher.doFinal(payload)

  def decrypt2[T: JsonDecoder](
      data: Array[Byte],
      keyData: Array[Byte],
      cipherType: CipherType = CipherType.AES_256_GCM,
      charset: Charset = UTF_8
  ): Option[(T, ZonedDateTime)] =
    val cipher = cipherType.newCipher
    val key    = SecretKeySpec(keyData, cipherType.name)
    val ivData = data.slice(0, cipherType.blockSize)
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(cipherType.tagLen, ivData))
    for
      payload    <- Try(cipher.doFinal(data.slice(cipherType.blockSize, data.length))).toOption
      payload    <- cipherType.padding.unpad(payload)
      payload    <- Json.decoder.decodeJson(charset.decode(ByteBuffer.wrap(payload))).toOption
      payload    <- payload.asObject
      data       <- payload.get("data")
      data       <- JsonDecoder[T].fromJsonAST(data).toOption
      expiration <- payload.get("exp")
      expiration <- expiration.asString.map(ZonedDateTime.parse(_, DateTimeFormatter.ISO_ZONED_DATE_TIME))
    yield (data, expiration)
