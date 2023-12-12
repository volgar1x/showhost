package xyz.volgar1x.showhost.persist.jdbc

import io.getquill.*
import xyz.volgar1x.showhost.ULID
import xyz.volgar1x.showhost.core.{File, FullStaticFile, Music, Playlist}
import xyz.volgar1x.showhost.persist.MusicPersist

import zio.{IO, UIO, ZIO, ZLayer}

import javax.sql.DataSource
import scala.annotation.tailrec

class JdbcMusicPersist(ds: DataSource) extends ZioJdbcContext(ds) with MusicPersist:
  private val playlistAllId      = ULID("all")
  inline given UpdateMeta[Music] = updateMeta(_.id)
  inline given UpdateMeta[File]  = updateMeta(_.id)

  override def get(musicId: ULID): IO[Option[Nothing], Music] =
    run:
      quote:
        query[Music].filter(_.id == lift(musicId)).take(1)
    .orDie
      .map(_.headOption)
      .some

  override def create(music: Music, file: File, cover: Option[FullStaticFile]): UIO[Music] =
    (for
      _ <- cover match
        case Some(cover) =>
          run(
            quote:
              query[FullStaticFile].insertValue(lift(cover))
          )
        case None => ZIO.unit
      _ <- run(
        quote:
          query[File].insertValue(lift(file))
      )
      _ <- run(
        quote:
          query[Music].insertValue(lift(music))
      )
    yield music).orDie

  override def update(music: Music): UIO[Music] =
    run:
      quote:
        query[Music].filter(_.id == lift(music.id)).updateValue(lift(music)).returning(m => m)
    .orDie

  override def list(q: MusicPersist.Query, page: Int = 0, pageLen: Int = 30): UIO[Seq[Music]] =
    @tailrec
    def foldMany[T](start: T, fns: List[T => T]): T =
      fns match
        case hd :: tl => foldMany(hd(start), tl)
        case Nil      => start

    run:
      foldMany(
        dynamicQuery[Music],
        List(
          q.title.foldLeft(_)((base, title) => base.filter(_.title.toLowerCase() like lift(s"%${title.toLowerCase()}%"))),
          q.artist
            .foldLeft(_)((base, artist) =>
              base.filter(m =>
                sql"EXISTS(SELECT 1 FROM json_array_elements_text(${m.artists}) AS a WHERE LOWER(a) LIKE ${lift(s"%${artist.toLowerCase()}%")})".asCondition
              )
            ),
          q.album.foldLeft(_)((base, album) => base.filter(_.album.exists(_.toLowerCase() like lift(s"%${album.toLowerCase()}%")))),
          q.genre.foldLeft(_)((base, genre) => base.filter(_.genre contains lift(genre)))
        )
      ).sortBy(_.id)(Ord.desc).drop(page * pageLen).take(pageLen)
    .orDie

  override def findFiles(musicIds: Set[ULID]): UIO[Map[ULID, File]] =
    run:
      quote:
        query[Music]
          .join(query[File])
          .on(_.fileId == _.id)
          .filter(lift(musicIds.toSeq) contains _._1.id)
          .map(x => x._1.id -> x._2)
    .orDie
      .map(_.toMap)

  override def fetchUserPlaylists(userId: ULID): UIO[Seq[Playlist]] =
    run:
      quote:
        query[Playlist].filter(_.userId == lift(userId))
    .orDie
      .map(Playlist(playlistAllId, userId, "All") :: _)

  override def findPlaylist(playlistId: ULID): UIO[Option[Playlist]] =
    if playlistId == playlistAllId
    then ZIO.succeed(Some(Playlist(playlistId, ULID("nobody"), "All")))
    else
      run:
        quote:
          query[Playlist].filter(_.id == lift(playlistId)).take(1)
      .orDie
        .map(_.headOption)

  override def fetchPlaylistContent(playlistIds: Set[ULID], page: Int, pageLen: Int): UIO[Map[ULID, Seq[Music]]] =
    run:
      quote:
        querySchema[(ULID, ULID)]("music_playlists", _._1 -> "music_id", _._2 -> "playlist_id")
          .join(query[Music])
          .on(_._1 == _.id)
          .filter(lift((playlistIds - playlistAllId).toSeq) contains _._1._2)
          .sortBy(_._2.id)(Ord.desc)
          .drop(lift(page * pageLen))
          .take(lift(pageLen))
    .orDie
      .map: rows =>
        rows.groupBy(_._1._2).view.mapValues(_.map(_._2)).toMap
      .flatMap: content =>
        if playlistIds.contains(playlistAllId)
        then
          for all <- list(MusicPersist.Query(), page, pageLen - content.valuesIterator.flatten.size)
          yield content.updated(playlistAllId, all)
        else ZIO.succeed(content)

object JdbcMusicPersist:
  def layer = ZLayer.derive[JdbcMusicPersist]
