package xyz.volgar1x.showhost

import io.azam.ulidj.{ULID as JULID}

import zio._
import zio.json.{JsonDecoder, JsonEncoder}

import java.time.temporal.ChronoUnit

case class ULID(value: String)

object ULID:
  def apply(time: Long, entropy: Chunk[Byte]): ULID = ULID(JULID.generate(time, entropy.toArray))

  def random: UIO[ULID] =
    for
      time    <- Clock.currentTime(ChronoUnit.MILLIS)
      entropy <- Random.nextBytes(10)
    yield ULID(time, entropy)

  given jsonEncoder: JsonEncoder[ULID] = JsonEncoder.string.contramap(_.value)
  given jsonDeocder: JsonDecoder[ULID] = JsonDecoder.string.map(ULID(_))

  given Ordering[ULID] with
    def compare(x: ULID, y: ULID): Int = Ordering[String].compare(x.value, y.value)
