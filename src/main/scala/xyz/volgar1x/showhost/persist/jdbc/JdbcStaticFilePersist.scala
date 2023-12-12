package xyz.volgar1x.showhost.persist.jdbc

import io.getquill.*
import xyz.volgar1x.showhost.ULID
import xyz.volgar1x.showhost.core.{FullStaticFile, StaticFile}
import xyz.volgar1x.showhost.persist.StaticFilePersist

import zio.*

import javax.sql.DataSource

class JdbcStaticFilePersist(ds: DataSource) extends ZioJdbcContext(ds) with StaticFilePersist:
  override def get(id: ULID): IO[Option[Nothing], StaticFile] =
    run:
      quote:
        query[StaticFile].filter(_.id == lift(id)).take(1)
    .orDie
      .map(_.headOption)
      .some

  override def getMany(ids: Set[ULID]): UIO[Seq[StaticFile]] =
    run:
      quote:
        query[StaticFile].filter(x => lift(ids.toSeq).contains(x.id))
    .orDie

  override def findByPath(path: String): UIO[Option[StaticFile]] =
    run:
      quote:
        query[StaticFile].filter(_.virtualPath contains lift(path)).take(1)
    .orDie
      .map(_.headOption)

  override def load(id: ULID): IO[Option[Nothing], Chunk[Byte]] =
    run:
      quote:
        query[FullStaticFile].filter(_.file.id == lift(id)).map(_.data).take(1)
    .orDie
      .map(_.headOption)
      .some
      .map(Chunk.fromArray)

  override def upload(meta: StaticFile, data: Chunk[Byte]): UIO[Unit] =
    run:
      quote:
        query[FullStaticFile].insertValue(FullStaticFile(lift(meta), lift(data.toArray)))
    .orDie.unit

object JdbcStaticFilePersist:
  def layer = ZLayer.derive[JdbcStaticFilePersist]
