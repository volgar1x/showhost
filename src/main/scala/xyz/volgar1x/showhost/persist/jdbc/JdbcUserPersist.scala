package xyz.volgar1x.showhost.persist.jdbc

import io.getquill.*
import xyz.volgar1x.showhost.ULID
import xyz.volgar1x.showhost.core.User.*
import xyz.volgar1x.showhost.core.{User, ViewHistory}
import xyz.volgar1x.showhost.persist.UserPersist

import zio.*

import javax.sql.DataSource

class JdbcUserPersist(ds: DataSource) extends ZioJdbcContext(ds) with UserPersist:
  inline given UpdateMeta[User] = updateMeta(_.id)

  override def get(id: ULID): IO[Option[Nothing], User] =
    run:
      quote:
        query[User].filter(_.id == lift(id)).take(1)
    .orDie
      .map(_.headOption)
      .some

  override def create(user: User): UIO[User] =
    run(
      quote:
        query[User].insertValue(lift(user))
    ).orDie.as(user)

  override def findByName(name: String): UIO[Option[User]] =
    run:
      quote:
        query[User].filter(_.name == lift(name)).take(1)
    .orDie
      .map(_.headOption)

  override def update(user: User): UIO[Unit] =
    run:
      quote:
        query[User].filter(_.id == lift(user.id)).updateValue(lift(user))
    .orDie.unit

  override def getVideoProgress(userId: ULID, videoId: ULID): UIO[Int] =
    run:
      quote:
        query[ViewHistory].filter(vh => vh.userId == lift(userId) && vh.videoId == lift(videoId)).take(1).map(_.progress)
    .orDie
      .map(_.headOption.getOrElse(0))

  override def recordVideoProgress(userId: ULID, vhId: ULID, progress: Int): UIO[Unit] =
    run:
      quote:
        query[ViewHistory]
          .filter(vh => vh.id == lift(vhId) && vh.userId == lift(userId))
          .update(_.progress -> lift(progress))
    .orDie.unit

object JdbcUserPersist:
  def layer = ZLayer.derive[JdbcUserPersist]
