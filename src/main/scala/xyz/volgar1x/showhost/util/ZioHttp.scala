package xyz.volgar1x.showhost.util

import zio.nio.Buffer
import zio.nio.channels.AsynchronousFileChannel
import zio.nio.file.Path
import zio.stream.ZStream
import zio.{http, *}

import java.io.{EOFException, IOException}
import java.nio.file.StandardOpenOption.READ

object ZioHttp:
  def urlencode(s: String): String =
    com.google.common.net.UrlEscapers.urlPathSegmentEscaper().escape(s)

  def fromPath(path: Path, contentType: String, bufferSize: Int = 5_000)(request: http.Request): ZIO[Any, IOException, http.Response] =
    for
      scope <- Scope.make
      ch    <- AsynchronousFileChannel.open(path, READ).provide(ZLayer.succeed(scope))
      sz    <- ch.size

      (start, stop) = request.header(http.Header.Range) match
        case Some(http.Header.Range.Single("bytes", start, stop)) => (start, stop.filter(_ < sz))
        case _                                                    => (0L, None)

      contentLength = stop.map(_ + 1).getOrElse(sz) - start

      posRef <- Ref.make(start)
      buffer <- Buffer.byte(bufferSize)
    yield
      val body =
        ZStream
          .repeatZIOChunkOption:
            (
              for
                pos   <- posRef.get
                count <- ch.read(buffer, pos)
                _     <- posRef.set(pos + count.toLong)
                _     <- buffer.flip
                chunk <- buffer.getChunk()
                _     <- buffer.clear
              yield chunk
            )
              .tapErrorCause(ZIO.logErrorCause(s"cannot read $path", _))
              .mapError:
                case _: EOFException => None
                case e               => Some(e)
          .ensuring(scope.close(Exit.unit))
      http.Response(
        headers = http.Headers(
          http.Header.AcceptRanges.Bytes,
          http.Header.Custom("content-range", s"bytes ${start}-${stop.map(_.toString()).getOrElse("")}/${sz}"),
          http.Header.Custom("content-type", contentType),
          http.Header.ContentLength(contentLength)
        ),
        body = http.Body.fromStream(body.take(contentLength))
      )
