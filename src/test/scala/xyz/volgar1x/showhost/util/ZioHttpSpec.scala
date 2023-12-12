package xyz.volgar1x.showhost.util

import zio.*
import zio.http.*
import zio.nio.file.Files
import zio.test.*

object ZioHttpSpec extends ZIOSpecDefault:
  override def spec = suite("ZioHttpSpec")(
    test("fromPath") {
      for
        path <- Files.createTempFileScoped()
        data <- Random.nextBytes(100)
        _    <- Files.writeBytes(path, data)

        headers = Headers(
          Header.Range.Single("bytes", 0, Some(99))
        )
        request = Request(Body.fromChunk(data), headers, Method.GET, URL(Path(Vector())), Version.`HTTP/1.1`, None)
        response <- ZioHttp.fromPath(path, "application/octet", bufferSize = 10)(request)
        body     <- response.body.asChunk
      yield assertTrue(body == data)
    },
    test("fromPath2") {
      for
        path <- Files.createTempFileScoped()
        data <- Random.nextBytes(100)
        _    <- Files.writeBytes(path, data)

        headers = Headers(
          Header.Range.Single("bytes", 0, Some(9))
        )
        request = Request(Body.fromChunk(data), headers, Method.GET, URL(Path(Vector())), Version.`HTTP/1.1`, None)
        response <- ZioHttp.fromPath(path, "application/octet", bufferSize = 10)(request)
        body     <- response.body.asChunk
      yield assertTrue(body == data.slice(0, 10))
    }
  )
