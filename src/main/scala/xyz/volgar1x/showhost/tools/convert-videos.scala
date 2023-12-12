package xyz.volgar1x.showhost.tools

import xyz.volgar1x.showhost.util.{`*`, indexBy, splitext, writeJson}
import xyz.volgar1x.showhost.{Process, ShowhostConfig, ffmpeg}

import zio.http.MediaType
import zio.nio.file.Files
import zio.{Console, Exit, URIO, ZIO, http}

import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.io.IOException

def convertVideos(paths: Seq[Path]): URIO[ShowhostConfig & Process, Unit] =
  ZIO
    .collectAll(paths.map(convertPath))
    .catchAll(err => Console.printLine(err.describe).ignoreLogged)
    .unit

def convertPath(path: Path): ZIO[ShowhostConfig & Process, Error, Unit] =
  Files
    .readAttributes[BasicFileAttributes](path)
    .mapError(IOError(_))
    .flatMap:
      case attrs if attrs.isDirectory() =>
        Files
          .list(path)
          .mapError(IOError(_))
          .runForeach(convertPath(_))

      case attrs if attrs.isRegularFile() =>
        path.splitext.filterNot(_._1.endsWith("_showhost")).flatMap(x => MediaType.forFileExtension(x._2.substring(1))) match
          case Some(m) if m.mainType == "video" =>
            convertFileWithFFmpeg(path)
          case _ =>
            ZIO.unit

      case _ =>
        ZIO.unit

def convertFileWithFFmpeg(src: Path): ZIO[ShowhostConfig & Process, Error, Unit] =
  val basename = src.splitext.map(_._1).getOrElse(src.getFileName().toString())
  val outDir   = src.getParent().resolve("showhost_out")
  for
    config <- ShowhostConfig()
      .filterOrFail(_.videoCodecs.nonEmpty)(ConfigError("Config \"videoCodec\" is empty"))
      .filterOrFail(_.audioCodecs.nonEmpty)(ConfigError("Config \"audioCodec\" is empty"))

    _ <- Files
      .readAttributes[BasicFileAttributes](outDir)
      .exit
      .flatMap:
        case Exit.Success(attrs) if attrs.isDirectory() => ZIO.unit
        case Exit.Success(_)                            => ZIO.fail(IOError(IOException(s"Path is expected to be a directory: ${outDir}")))
        case Exit.Failure(_)                            => Files.createDirectory(outDir).mapError(IOError(_))

    meta <- ffmpeg.probe(src).mapError(IOError(_))

    inVideoStreams    = meta.streams.filter(_.codecType == ffmpeg.CodecType.video)
    inAudioStreams    = meta.streams.filter(_.codecType == ffmpeg.CodecType.audio)
    inSubtitleStreams = meta.streams.filter(_.codecType == ffmpeg.CodecType.subtitle)

    outMediaStreams = (inVideoStreams * inAudioStreams).zipWithIndex
      .indexBy: (_, idx) =>
        outDir.resolve(s"${basename}_${idx}_showhost.mp4")
      .view
      .mapValues(_._1)
      .toMap

    outSubtitleStreams = inSubtitleStreams.zipWithIndex
      .indexBy: (_, idx) =>
        outDir.resolve(s"${basename}_${outMediaStreams.size + idx}_showhost.txt")
      .view
      .mapValues(_._1)
      .toMap

    _ <- ZIO
      .collectAll(
        outMediaStreams.map:
          case (dst, (video, audio)) =>
            ZIO
              .ifZIO(Files.exists(dst))(
                ZIO.logWarning(s"File already exists, skipping: ${dst}"),
                ffmpeg.run(
                  src,
                  dst,
                  format = ffmpeg.OutputFormat.mp4,
                  targets = List(
                    ffmpeg.TargetStream(video, config.videoCodecs),
                    ffmpeg.TargetStream(audio, config.audioCodecs)
                  )
                )
              )
      )
      .mapError(IOError(_))

    _ <- ZIO
      .collectAll(
        outSubtitleStreams.map: (dst, subtitle) =>
          ZIO.ifZIO(Files.exists(dst))(
            ZIO.logWarning(s"File already exists, skipping: ${dst}"),
            ffmpeg.run(
              src,
              dst,
              format = ffmpeg.OutputFormat.webvtt,
              targets = List(ffmpeg.TargetStream(subtitle, List("webvtt")))
            )
          )
      )
      .mapError(IOError(_))

    manifestDest = outDir.resolve(s"${basename}_showhost.json")
    _ <- writeJson(
      manifestDest,
      Manifest.create(src, manifestDest, meta.format, outMediaStreams, outSubtitleStreams)
    ).mapError(IOError(_))
  yield ()
