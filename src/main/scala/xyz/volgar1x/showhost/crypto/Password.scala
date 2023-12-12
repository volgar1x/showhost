package xyz.volgar1x.showhost.crypto

import com.google.common.io.BaseEncoding

import zio.*

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.charset.{Charset, StandardCharsets}
import java.security.MessageDigest

final class Password(val hash: Array[Byte]):
  import Password.Hash.SHA_256

  def withClearText(
      clearText: String,
      rounds: Short = 10_000,
      hashType: Password.Hash = SHA_256,
      charset: Charset = UTF_8,
      iv: Option[Chunk[Byte]] = None
  ): UIO[Password] =
    Password.fromClearText(clearText, rounds, hashType, charset, Some(iv.getOrElse(Chunk.fromArray(hash).slice(0, hashType.blockSize))))

  def verify(clearText: String, rounds: Short = 10_000, hashType: Password.Hash = SHA_256, charset: Charset = UTF_8): UIO[Boolean] =
    withClearText(clearText).map(_ sameHash hash)

  def sameHash(other: Array[Byte]): Boolean =
    hash.iterator.zip(other).forall { (a, b) => a == b }

  override def hashCode(): Int =
    hash.foldLeft(0)(_ + _)

  override def equals(x$1: Any): Boolean =
    x$1 match
      case other: Password => other sameHash other.hash
      case _               => false

  override def toString(): String =
    val repr = BaseEncoding.base16().encode(hash)
    s"Password($repr)"

object Password:
  enum Hash(val name: String, val blockSize: Int):
    def newMessageDigest: MessageDigest = MessageDigest.getInstance(name)
    case SHA_256 extends Hash("SHA-256", 64)
    case SHA_512 extends Hash("SHA-512", 128)

  def fromClearText(
      clearText: String,
      rounds: Short = 10_000,
      hashType: Hash = Hash.SHA_256,
      charset: Charset = UTF_8,
      iv: Option[Chunk[Byte]] = None
  ): UIO[Password] =
    for iv <- ZIO
        .fromOption(iv)
        .filterOrDie(_.size == hashType.blockSize)(IllegalArgumentException())
        .catchAll(_ => Random.nextBytes(hashType.blockSize))
    yield fromClearText2(clearText, iv, rounds, hashType, charset)

  def fromClearText2(
      clearText: String,
      iv: Chunk[Byte],
      rounds: Short = 10_000,
      hashType: Hash = Hash.SHA_256,
      charset: Charset = UTF_8
  ): Password =
    val iv2 = iv.toArray
    val md  = hashType.newMessageDigest
    val hash = (0 until rounds).foldLeft(charset.encode(clearText).toArray)((acc, round) =>
      md.reset()
      md.update(iv2)
      md.update((round & 0xff).asInstanceOf[Byte])
      md.update((round >> 8).asInstanceOf[Byte])
      md.update(acc.toArray)
      md.digest()
    )
    Password(iv2 ++ hash)
