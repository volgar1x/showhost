package xyz.volgar1x.showhost.persist.jdbc

import io.getquill.*
import xyz.volgar1x.showhost.ULID
import xyz.volgar1x.showhost.core.{File, Video, VideoFile, ViewHistory}
import xyz.volgar1x.showhost.persist.VideoPersist

import zio.*

import java.time.LocalDateTime
import javax.sql.DataSource

class JdbcVideoPersist(ds: DataSource) extends ZioJdbcContext(ds) with VideoPersist:
  inline given UpdateMeta[Video] = updateMeta(_.id)

  override def get(videoId: ULID): IO[Option[Nothing], Video] =
    run:
      quote:
        query[Video].filter(_.id == lift(videoId)).take(1)
    .orDie
      .map(_.headOption)
      .some

  override def create(video: Video, files: Seq[File]): UIO[Video] =
    transaction:
      for
        existingFiles <- run(
          quote:
            query[File].filter(f => lift(files.map(_.id)).contains(f.id)).map(_.id)
        )
        _ <- run(
          quote:
            liftQuery(files.filterNot(f => existingFiles.contains(f.id))).foreach: f =>
              query[File].insertValue(f)
        )
        _ <- run(
          quote:
            query[Video].insertValue(lift(video))
        )
        _ <- run(
          quote:
            liftQuery(files.map(f => video.id -> f.id)).foreach: vf =>
              querySchema[(ULID, ULID)]("video_files", _._1 -> "video_id", _._2 -> "file_id")
                .insertValue(vf)
        )
      yield video
    .orDie

  override def update(video: Video): UIO[Video] =
    run(
      quote:
        query[Video].filter(_.id == lift(video.id)).updateValue(lift(video))
    ).orDie.as(video)

  override def findByItem(itemId: Set[ULID]): UIO[Seq[Video]] =
    run:
      quote:
        query[Video].filter(v => lift(itemId.toSeq).contains(v.libraryItemId))
    .orDie

  override def findFiles(videoIds: Set[ULID]): UIO[Map[ULID, Seq[File]]] =
    run:
      quote:
        query[VideoFile]
          .join(query[File])
          .on(_.fileId == _.id)
          .filter(lift(videoIds.toSeq) contains _._1.videoId)
          .map(x => (x._1.videoId, x._2))
    .orDie
      .map(_.groupMap(_._1)(_._2))

  override def fetchViewHistoryByItem(userId: ULID, itemIds: Set[ULID]): UIO[Seq[ViewHistory]] =
    if itemIds.isEmpty
    then
      run:
        quote:
          query[ViewHistory].filter(_.userId == lift(userId)).sortBy(_.viewedAt)(Ord.descNullsLast)
      .orDie
    else
      run:
        quote:
          query[ViewHistory]
            .join(query[Video])
            .on(_.videoId == _.id)
            .filter(x => x._1.userId == lift(userId) && lift(itemIds.toSeq).contains(x._2.libraryItemId))
            .sortBy(_._1.viewedAt)(Ord.descNullsLast)
            .map(_._1)
      .orDie

  private val defaultSessionDuration = Duration.fromJava(java.time.Duration.ofDays(3))

  override def genViewSession(userId: ULID, videoId: ULID, sessionDuration: Option[Duration] = None): UIO[ViewHistory] =
    val dur = sessionDuration.getOrElse(defaultSessionDuration)

    for
      now <- Clock.localDateTime
      limit = now.minus(dur)

      history <- run(
        quote:
          query[ViewHistory]
            .filter(vh => vh.userId == lift(userId) && vh.videoId == lift(videoId) && sql"${vh.viewedAt} >= ${lift(limit)}".asCondition)
            .sortBy(_.viewedAt)(Ord.desc)
            .take(1)
      ).orDie
        .map(_.headOption)
        .some
        .orElse:
          for
            id <- ULID.random
            vh = ViewHistory(id, userId, videoId, now, 0)
            vh2 <- run(
              quote:
                query[ViewHistory].insertValue(lift(vh)).returning(vh => vh)
            ).orDie
          yield vh2
    yield history

object JdbcVideoPersist:
  def layer = ZLayer.derive[JdbcVideoPersist]
