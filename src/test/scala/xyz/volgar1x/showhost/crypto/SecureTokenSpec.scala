package xyz.volgar1x.showhost.crypto

import zio.*
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}
import zio.test.*

import java.time.ZoneId
import scala.collection.Factory

case class MyTokenData(privateData: String)

object SecureTokenSpec extends ZIOSpecDefault:
  given myTokenDataEncoder: JsonEncoder[MyTokenData] = DeriveJsonEncoder.gen
  given myTokenDataDecoder: JsonDecoder[MyTokenData] = DeriveJsonDecoder.gen
  given timeZone: ZoneId                             = ZoneId.systemDefault()

  private def myTokenData = MyTokenData("this is very private")
  private def cross[A, B, Col](xs: IterableOnce[A], ys: Iterable[B])(using factory: Factory[(A, B), Col]): Col =
    xs.iterator.flatMap(x => ys.map(y => (x, y))).to(factory)

  // private def validPayload = b16decode(
  //   "15931303FC9AFA09FD8AC1CC3D9010B0226D3D0DF1F3C475684457BF656DD59E4D6241EAB450F253905939A206354DC2605215280C290004C405CE3679C77C8F7B293097B064D54067E0CBCED7485A15D9F9957FC2770491AA9B3BC8A00BF022EB2CD1D55E311EF9B3759A1BAC0770D56ECB2DB516056BE85A71E5DC93F821247E84E6392E377F33DEA8EF2468A80D843B95243BDD0462F14DD0F8FFA7C0EBCA2AA6B3ED17A30A9E2E4B632B4CCD89"
  // )
  // private def validPrivKey = b16decode("B8CCC8B233121A19313E349BAFADA4113E364A01D578007A02DC54F1D1F30340")
  // private def validDecrypt = (validPayload, validPrivKey)

  override def spec = suite("SecureTokenSpec")(
    test("encrypt=>decrypt") {
      for
        privKey <- SecureToken.randomKey()
        // _          <- Console.printLine(s"privKey=${b16encode(privKey)}")
        publicData <- SecureToken.encrypt(myTokenData, privKey, 30.days)
        // _          <- Console.printLine(s"publicData=${b16encode(publicData)}")
        privData <- SecureToken.decrypt[MyTokenData](publicData._1, privKey)
      yield assertTrue(
        myTokenData == privData
        // b16decode(b16encode(privKey)).length == privKey.length,
        // b16decode(b16encode(privKey)).zip(privKey).forall((l, r) => l == r)
      )
    },
    test("randomness") {
      val N = 100
      for
        privKey <- SecureToken.randomKey()
        tokens  <- ZIO.collectAll((0 until N).map(_ => SecureToken.encrypt(myTokenData, privKey, 30.days))).map(_.map(x => x._1.encoded))
      yield assertTrue(cross(tokens, tokens).filter(_ != _).size == N * (N - 1))
    }
    // test("decrypt invalid iv") {
    //   for
    //     _      <- TestClock.setTime(Instant.ofEpochSecond(1695578855))
    //     result <- SecureToken.decrypt[MyTokenData](validPayload, validPrivKey).exit
    //   yield assertTrue(result.isFailure)
    // }.provide(
    //   ZLayer.succeed(Clock.ClockLive)
    // ),
    // test("decrypt invalid data") {
    //   for
    //     _      <- TestClock.setTime(Instant.ofEpochSecond(1695578855))
    //     result <- SecureToken.decrypt[MyTokenData](validPayload, validPrivKey).exit
    //   yield assertTrue(result.isFailure)
    // }.provide(
    //   ZLayer.succeed(Clock.ClockLive)
    // ),
    // test("decrypt invalid key") {
    //   for
    //     _      <- TestClock.setTime(Instant.ofEpochSecond(1695578855))
    //     result <- SecureToken.decrypt[MyTokenData](validPayload, validPrivKey).exit
    //   yield assertTrue(result.isFailure)
    // }.provide(
    //   ZLayer.succeed(Clock.ClockLive)
    // ),
    // test("decrypt expired") {
    //   for
    //     _      <- TestClock.setTime(Instant.ofEpochSecond(1700762855))
    //     result <- SecureToken.decrypt[MyTokenData](validPayload, validPrivKey).exit
    //   yield assertTrue(result.isFailure)
    // }.provide(
    //   ZLayer.succeed(Clock.ClockLive)
    // )
  )
