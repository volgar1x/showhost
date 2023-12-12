package xyz.volgar1x.showhost

import zio.ZIO
import zio.json.JsonDecoder

import java.io.IOException
import java.nio.file.Path

package object ffmpeg:
  def probe(path: Path): ZIO[Process, IOException, ProbeResult] =
    for
      outs <- Process.exec(
        "ffprobe",
        Seq(
          "-v",
          "error",
          "-of",
          "json",
          "-show_streams",
          "-show_format",
          path.toString
        )
      )
      result <- ZIO.fromEither(JsonDecoder[ProbeResult].decodeJson(outs._1)).mapError(error => IOException(s"cannot parse json: $error"))
    yield result

  enum OutputFormat(val opts: Seq[String]):
    case mp4    extends OutputFormat(Seq("-pix_fmt", "yuv420p", "-movflags", "faststart"))
    case webvtt extends OutputFormat(Seq.empty)

  private val channelLayouts = List("7.1", "5.1", "stereo", "mono")

  def run(from: Path, dest: Path, format: OutputFormat, targets: Seq[TargetStream]): ZIO[Process, IOException, Unit] =
    val baseArgv = Seq(
      "-v",
      "error",
      "-stats",
      "-hwaccel",
      "auto"
    )

    val streamArgv =
      Seq("-f", format.toString())
        ++ format.opts
        ++ targets.zipWithIndex.flatMap((t, i) =>
          Seq(
            "-map",
            s"0:${t.source.index}",
            s"-c:$i",
            if t.targetCodecNames.contains(t.source.codecName) && t.source.channelLayout.forall(channelLayouts.contains(_))
            then "copy"
            else t.targetCodecNames.head
          )
        )
        ++ (if targets.exists(_.source.channelLayout.exists(l => !channelLayouts.contains(l)))
            then Seq("-af", s"aformat=channel_layouts=${channelLayouts.mkString("|")}")
            else Seq())

    val argv = baseArgv ++ Seq("-i", from.toString()) ++ streamArgv :+ dest.toString()

    Process.execLive("ffmpeg", argv)
