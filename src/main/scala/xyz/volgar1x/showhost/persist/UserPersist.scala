package xyz.volgar1x.showhost.persist

import xyz.volgar1x.showhost.ULID
import xyz.volgar1x.showhost.core.User

import zio._

trait UserPersist:
  def get(id: ULID): IO[Option[Nothing], User]
  def create(user: User): UIO[User]
  def findByName(name: String): UIO[Option[User]]
  def update(user: User): UIO[Unit]
  def getVideoProgress(userId: ULID, videoId: ULID): UIO[Int]
  def recordVideoProgress(userId: ULID, vhId: ULID, progress: Int): UIO[Unit]

object UserPersist:
  def get(id: ULID): ZIO[UserPersist, Option[Nothing], User] =
    ZIO.serviceWithZIO[UserPersist](_.get(id))

  def create(user: User): URIO[UserPersist, User] =
    ZIO.serviceWithZIO[UserPersist](_.create(user))

  def findByName(name: String): URIO[UserPersist, Option[User]] =
    ZIO.serviceWithZIO[UserPersist](_.findByName(name))

  def update(user: User): URIO[UserPersist, Unit] =
    ZIO.serviceWithZIO[UserPersist](_.update(user))

  def getVideoProgress(userId: ULID, videoId: ULID): URIO[UserPersist, Int] =
    ZIO.serviceWithZIO(_.getVideoProgress(userId, videoId))

  def recordVideoProgress(userId: ULID, vhId: ULID, progress: Int): URIO[UserPersist, Unit] =
    ZIO.serviceWithZIO(_.recordVideoProgress(userId, vhId, progress))
