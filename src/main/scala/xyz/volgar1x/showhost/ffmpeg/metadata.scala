package xyz.volgar1x.showhost.ffmpeg

import zio.json.ast.Json
import zio.json.{DeriveJsonCodec, JsonCodec, JsonDecoder, JsonEncoder, SnakeCase, jsonMemberNames}

enum CodecType:
  case video, audio, subtitle

object CodecType:
  given JsonCodec[CodecType] = JsonCodec(
    JsonEncoder.string.contramap(_.toString()),
    JsonDecoder.string.map(CodecType.valueOf)
  )

@jsonMemberNames(SnakeCase)
final case class FFmpegStream(
    index: Int,
    codecName: String,
    codecType: CodecType,
    tags: Option[Json.Obj],
    channels: Option[Int],
    channelLayout: Option[String]
)

final case class TargetStream(
    source: FFmpegStream,
    targetCodecNames: Seq[String]
)

object FFmpegStream:
  given JsonCodec[FFmpegStream] = DeriveJsonCodec.gen

@jsonMemberNames(SnakeCase)
final case class FFmpegFormat(
    formatName: String,
    duration: BigDecimal,
    size: BigInt
)

object FFmpegFormat:
  given JsonCodec[FFmpegFormat] = DeriveJsonCodec.gen

@jsonMemberNames(SnakeCase)
final case class ProbeResult(
    streams: Seq[FFmpegStream],
    format: FFmpegFormat
)

object ProbeResult:
  given JsonCodec[ProbeResult] = DeriveJsonCodec.gen
