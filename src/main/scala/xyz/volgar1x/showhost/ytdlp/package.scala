package xyz.volgar1x.showhost

import zio.ZIO
import zio.http.{MediaType, URL}
import zio.json.{DeriveJsonCodec, JsonCodec, JsonDecoder}

import java.io.IOException
import java.nio.file.{Files, Path}

package object ytdlp:
  final case class Info(
      duration: Int
  )

  final case class Result(
      info: Info,
      path: Path,
      size: Long,
      `type`: MediaType,
      coverPath: Path,
      coverType: MediaType,
      infoJsonPath: Path
  )

  given JsonCodec[Info] = DeriveJsonCodec.gen

  def run(url: URL, dest: Path): ZIO[ShowhostConfig & Process, IOException, Result] =
    val path         = dest.getParent().resolve(s"${dest.getFileName()}.opus")
    val coverPath    = dest.getParent().resolve(s"${dest.getFileName()}.webp")
    val infoJsonPath = dest.getParent().resolve(s"${dest.getFileName()}.info.json")
    for
      codec <- ShowhostConfig.get(_.musicCodec)
      _ <- Process.execLive(
        "yt-dlp",
        Seq(
          "--no-playlist",
          "--no-progress",
          "--no-simulate",
          "--extract-audio",
          "--audio-quality",
          "0",
          "--audio-format",
          codec,
          "--write-thumbnail",
          "--convert-thumbnails",
          "webp",
          "--write-info-json",
          "-o",
          dest.toString(),
          url.encode
        )
      )

      info <- ZIO
        .attemptBlockingIO(Files.readString(infoJsonPath))
        .flatMap(contents => ZIO.fromEither(JsonDecoder[Info].decodeJson(contents)).mapError(msg => IOException(msg)))

      fileSize <- ZIO.attemptBlockingIO(Files.size(path))
    yield Result(info, path, fileSize, MediaType.audio.opus, coverPath, MediaType.image.webp, infoJsonPath)
