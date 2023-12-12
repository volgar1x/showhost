package xyz.volgar1x.showhost.core

import xyz.volgar1x.showhost.ULID

import java.time.LocalDate

case class Video(
    id: ULID,
    libraryItemId: ULID,
    name: String,
    summary: Option[String],
    season: Option[Int],
    episode: Option[Int],
    coverUrl: Option[String],
    released: Option[LocalDate],
    duration: Int
)

case class VideoFile(
    videoId: ULID,
    fileId: ULID
)
