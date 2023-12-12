package xyz.volgar1x.showhost.persist

import xyz.volgar1x.showhost.ULID
import xyz.volgar1x.showhost.core.{File, Video, ViewHistory}

import zio.*

trait VideoPersist:
  def get(videoId: ULID): IO[Option[Nothing], Video]
  def create(video: Video, files: Seq[File]): UIO[Video]
  def update(video: Video): UIO[Video]
  def findByItem(itemIds: Set[ULID]): UIO[Seq[Video]]
  def findFiles(videoIds: Set[ULID]): UIO[Map[ULID, Seq[File]]]
  def fetchViewHistoryByItem(userId: ULID, itemIds: Set[ULID]): UIO[Seq[ViewHistory]]
  def genViewSession(userId: ULID, videoId: ULID, sessionDuration: Option[Duration] = None): UIO[ViewHistory]

object VideoPersist:
  def get(videoId: ULID): ZIO[VideoPersist, Option[Nothing], Video] =
    ZIO.serviceWithZIO(_.get(videoId))

  def findByItem(itemIds: Set[ULID]): URIO[VideoPersist, Seq[Video]] =
    ZIO.serviceWithZIO(_.findByItem(itemIds))

  def findFiles(videoIds: Set[ULID]): URIO[VideoPersist, Map[ULID, Seq[File]]] =
    ZIO.serviceWithZIO(_.findFiles(videoIds))

  def fetchViewHistoryByItem(userId: ULID, itemIds: Set[ULID]): URIO[VideoPersist, Seq[ViewHistory]] =
    ZIO.serviceWithZIO(_.fetchViewHistoryByItem(userId, itemIds))

  def genViewSession(userId: ULID, videoId: ULID, sessionDuration: Option[Duration] = None): URIO[VideoPersist, ViewHistory] =
    ZIO.serviceWithZIO(_.genViewSession(userId, videoId, sessionDuration))

  def create(video: Video, files: Seq[File]): URIO[VideoPersist, Video] =
    ZIO.serviceWithZIO(_.create(video, files))

  def update(video: Video): URIO[VideoPersist, Video] =
    ZIO.serviceWithZIO(_.update(video))
