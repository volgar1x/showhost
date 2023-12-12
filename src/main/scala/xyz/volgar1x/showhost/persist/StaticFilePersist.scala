package xyz.volgar1x.showhost.persist

import xyz.volgar1x.showhost.ULID
import xyz.volgar1x.showhost.core.StaticFile

import zio.*

trait StaticFilePersist:
  def get(id: ULID): IO[Option[Nothing], StaticFile]
  def getMany(ids: Set[ULID]): UIO[Seq[StaticFile]]
  def findByPath(path: String): UIO[Option[StaticFile]]
  def load(id: ULID): IO[Option[Nothing], Chunk[Byte]]
  def upload(meta: StaticFile, data: Chunk[Byte]): UIO[Unit]

object StaticFilePersist:
  def get(id: ULID): ZIO[StaticFilePersist, Option[Nothing], StaticFile] =
    ZIO.serviceWithZIO(_.get(id))

  def getMany(ids: Set[ULID]): URIO[StaticFilePersist, Seq[StaticFile]] =
    ZIO.serviceWithZIO(_.getMany(ids))

  def findByPath(path: String): URIO[StaticFilePersist, Option[StaticFile]] =
    ZIO.serviceWithZIO(_.findByPath(path))

  def load(id: ULID): ZIO[StaticFilePersist, Option[Nothing], Chunk[Byte]] =
    ZIO.serviceWithZIO(_.load(id))

  def upload(meta: StaticFile, data: Chunk[Byte]): URIO[StaticFilePersist, Unit] =
    ZIO.serviceWithZIO(_.upload(meta, data))
