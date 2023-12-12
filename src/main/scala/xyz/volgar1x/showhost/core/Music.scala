package xyz.volgar1x.showhost.core

import io.getquill.JsonValue
import xyz.volgar1x.showhost.ULID

case class Music(
    id: ULID,
    duration: Int,
    title: String,
    artists: JsonValue[Seq[String]],
    album: Option[String],
    genre: Option[String],
    fileId: ULID,
    coverId: Option[ULID],
    sourceUrl: Option[String]
)
