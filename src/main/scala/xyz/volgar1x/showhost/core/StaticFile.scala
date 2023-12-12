package xyz.volgar1x.showhost.core

import io.getquill.{SchemaMeta, schemaMeta}
import xyz.volgar1x.showhost.ULID

case class StaticFile(
    id: ULID,
    virtualPath: Option[String],
    extension: String,
    mimeType: String,
    size: Int
) {
  def filename: String = virtualPath.getOrElse(s"${id.value}${extension}")
}

case class FullStaticFile(file: StaticFile, data: Array[Byte])

object FullStaticFile:
  inline given SchemaMeta[FullStaticFile] = schemaMeta("static_files")
