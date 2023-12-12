package xyz.volgar1x.showhost.crypto

import zio.*
import zio.test.*

object PasswordSpec extends ZIOSpecDefault:
  override def spec = suite("PasswordSpec")(
    test("withClearText") {
      for
        password  <- Password.fromClearText("hello_world")
        password2 <- password.withClearText("hello_world")
      yield assertTrue(password == password2)
    },
    test("verify") {
      for
        password <- Password.fromClearText("hello_world")
        verify   <- password.verify("hello_world")
      yield assertTrue(verify)
    },
    test("no verify") {
      for
        password <- Password.fromClearText("hello_world")
        verify   <- password.verify("abcdefgh")
      yield assertTrue(!verify)
    }
  )
