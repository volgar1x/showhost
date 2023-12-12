package xyz.volgar1x.showhost.core

import xyz.volgar1x.showhost.ULID

case class File(
    id: ULID,
    mimeType: String,
    size: Long,
    localPath: String,
    lang: Option[String]
)
