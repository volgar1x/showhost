package xyz.volgar1x.showhost.persist.jdbc

import io.getquill.*
import xyz.volgar1x.showhost.ULID
import xyz.volgar1x.showhost.core.{LibraryItem, Video, ViewHistory}
import xyz.volgar1x.showhost.persist.LibraryItemPersist

import zio.*
import zio.json.JsonDecoder.*

import javax.sql.DataSource

class JdbcLibraryItemPersist(ds: DataSource) extends ZioJdbcContext(ds) with LibraryItemPersist:
  import LibraryItem.*

  inline given UpdateMeta[LibraryItem] = updateMeta(_.id)

  override def get(id: ULID): IO[Option[Nothing], LibraryItem] =
    run:
      quote:
        query[LibraryItem].filter(_.id == lift(id)).take(1)
    .orDie
      .map(_.headOption)
      .some

  def create(item: LibraryItem): UIO[Unit] =
    run:
      quote:
        query[LibraryItem].insertValue(lift(item))
    .orDie.unit

  def update(item: LibraryItem): UIO[Unit] =
    run:
      quote:
        query[LibraryItem].filter(_.id == lift(item.id)).updateValue(lift(item))
    .orDie.unit

  override def list(
      itemType: Seq[LibraryItem.Type],
      page: Int = 0,
      pageLen: Int = 30
  ): UIO[Seq[LibraryItem]] =
    run:
      quote:
        query[LibraryItem]
          .sortBy(_.id)(Ord.desc)
          .filter(it => lift(itemType).contains(it.itemType))
          .drop(lift(page * pageLen))
          .take(lift(pageLen))
    .orDie

  override def recent(
      itemType: Seq[LibraryItem.Type],
      page: Int = 0,
      pageLen: Int = 30
  ): UIO[Seq[LibraryItem]] =
    quote:
      query[ViewHistory]
        .sortBy(_.viewedAt)(Ord.desc)

    run:
      quote:
        query[LibraryItem]
          .sortBy(it => (it.nextRelease, it.lastReleased))(Ord.ascNullsLast)
          .filter(it => lift(itemType).contains(it.itemType))
          .drop(lift(page * pageLen))
          .take(lift(pageLen))
    .orDie

  override def lastSeenBy(
      userId: ULID,
      itemType: Seq[LibraryItem.Type],
      page: Int = 0,
      pageLen: Int = 30
  ): UIO[Seq[LibraryItem]] =
    run:
      quote:
        query[ViewHistory]
          .join(query[Video])
          .on(_.videoId == _.id)
          .join(query[LibraryItem])
          .on(_._2.libraryItemId == _.id)
          .filter(_._1._1.userId == lift(userId))
          .filter(x => lift(itemType).contains(x._2.itemType))
          .groupByMap(_._2.id)(x => (x._2.id, max(x._1._1.viewedAt)))
          .join(query[LibraryItem])
          .on(_._1 == _.id)
          .sortBy(_._1._2)(Ord.desc)
          .map(_._2)
          .drop(lift(page * pageLen))
          .take(lift(pageLen))
    .orDie

  override def newTvShows(): UIO[Seq[LibraryItem]] =
    run(
      sql"""select tmp0.*
from (
  select
    li.*,
    array[
      (li.next_release_meta->>'season_number')::integer,
      (li.next_release_meta->>'episode_number')::integer
    ] as coding
  from library_items li
  where li.next_release_meta is not null
) as tmp0, (
  select
    library_item_id,
    max(array[
      coalesce(season, 1),
      episode
    ]) as coding
  from videos
  group by library_item_id
) as tmp1
where tmp0.id=tmp1.library_item_id
and tmp0.coding > tmp1.coding
order by tmp0.next_release asc nulls last
""".as[Query[LibraryItem]]
    ).orDie

object JdbcLibraryItemPersist:
  def layer = ZLayer.derive[JdbcLibraryItemPersist]
