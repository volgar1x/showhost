package xyz.volgar1x.showhost.persist

import xyz.volgar1x.showhost.ULID
import xyz.volgar1x.showhost.core.LibraryItem

import zio.*

trait LibraryItemPersist:
  def get(id: ULID): IO[Option[Nothing], LibraryItem]
  def create(item: LibraryItem): UIO[Unit]
  def update(item: LibraryItem): UIO[Unit]

  def list(
      itemType: Seq[LibraryItem.Type],
      page: Int = 0,
      pageLen: Int = 30
  ): UIO[Seq[LibraryItem]]

  def recent(
      itemType: Seq[LibraryItem.Type],
      page: Int = 0,
      pageLen: Int = 30
  ): UIO[Seq[LibraryItem]]

  def lastSeenBy(
      userId: ULID,
      itemType: Seq[LibraryItem.Type],
      page: Int = 0,
      pageLen: Int = 30
  ): UIO[Seq[LibraryItem]]

  def newTvShows(): UIO[Seq[LibraryItem]]

object LibraryItemPersist:
  def get(id: ULID): ZIO[LibraryItemPersist, Option[Nothing], LibraryItem] =
    ZIO.serviceWithZIO(_.get(id))

  def create(item: LibraryItem): URIO[LibraryItemPersist, Unit] =
    ZIO.serviceWithZIO(_.create(item))

  def update(item: LibraryItem): URIO[LibraryItemPersist, Unit] =
    ZIO.serviceWithZIO(_.update(item))

  def list(
      itemType: Seq[LibraryItem.Type],
      page: Int = 0,
      pageLen: Int = 30
  ): URIO[LibraryItemPersist, Seq[LibraryItem]] =
    ZIO.serviceWithZIO(_.list(itemType, page, pageLen))

  def recent(
      itemType: Seq[LibraryItem.Type],
      page: Int = 0,
      pageLen: Int = 30
  ): URIO[LibraryItemPersist, Seq[LibraryItem]] =
    ZIO.serviceWithZIO(_.recent(itemType, page, pageLen))

  def lastSeenBy(
      userId: ULID,
      itemType: Seq[LibraryItem.Type],
      page: Int = 0,
      pageLen: Int = 30
  ): URIO[LibraryItemPersist, Seq[LibraryItem]] =
    ZIO.serviceWithZIO(_.lastSeenBy(userId, itemType, page, pageLen))

  def newTvShows(): URIO[LibraryItemPersist, Seq[LibraryItem]] =
    ZIO.serviceWithZIO(_.newTvShows())
