package xyz.volgar1x.showhost.tools
import xyz.volgar1x.showhost.ffmpeg

import zio.json.ast.Json
import zio.{Chunk, json}

import java.nio.file.Path
import java.time.{OffsetDateTime, ZoneOffset}

final case class ManifestFile(
    filename: String,
    mimeType: String,
    duration: Int,
    coding: Map[String, Int],
    tags: Json.Obj
)

final case class Manifest(
    src: String,
    date: OffsetDateTime,
    files: Seq[ManifestFile]
)

object Manifest:
  given json.JsonCodec[ManifestFile] = json.DeriveJsonCodec.gen
  given json.JsonCodec[Manifest]     = json.DeriveJsonCodec.gen

  def create(
      src: Path,
      dst: Path,
      format: ffmpeg.FFmpegFormat,
      mediaStreams: Map[Path, (ffmpeg.FFmpegStream, ffmpeg.FFmpegStream)],
      textStreams: Map[Path, ffmpeg.FFmpegStream]
  ): Manifest =
    val mediaFiles =
      mediaStreams
        .map:
          case (streamDst, (video, audio)) =>
            val coding    = findVideoCodingInFilename(dst.getFileName().toString())
            val videoTags = video.tags.map(_.fields).getOrElse(Chunk.empty)
            val audioTags = audio.tags.map(_.fields).getOrElse(Chunk.empty)
            val tags      = videoTags ++ audioTags

            ManifestFile(
              dst.getParent().relativize(streamDst).toString(),
              "video/mp4",
              format.duration.toFloat.ceil.toInt,
              coding,
              Json.Obj(tags)
            )
        .toSeq

    val textFiles = textStreams
      .map: (streamDst, subtitle) =>
        val coding = findVideoCodingInFilename(streamDst.getFileName().toString())
        ManifestFile(
          dst.getParent().relativize(streamDst).toString(),
          "text/vtt",
          format.duration.toFloat.ceil.toInt,
          coding,
          subtitle.tags.getOrElse(Json.Obj())
        )
      .toSeq

    Manifest(
      src = dst.getParent().relativize(src).toString(),
      date = OffsetDateTime.now(ZoneOffset.UTC),
      files = mediaFiles ++ textFiles
    )
