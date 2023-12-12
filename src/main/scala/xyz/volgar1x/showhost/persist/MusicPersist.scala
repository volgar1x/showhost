package xyz.volgar1x.showhost.persist

import xyz.volgar1x.showhost.ULID
import xyz.volgar1x.showhost.core.{File, FullStaticFile, Music, Playlist}

import zio.*

trait MusicPersist:
  import MusicPersist.*

  def get(musicId: ULID): IO[Option[Nothing], Music]
  def create(music: Music, file: File, cover: Option[FullStaticFile]): UIO[Music]
  def update(music: Music): UIO[Music]
  def list(query: Query, page: Int = 0, pageLen: Int = 30): UIO[Seq[Music]]
  def findFiles(musicIds: Set[ULID]): UIO[Map[ULID, File]]
  def findFile(musicId: ULID): UIO[Option[File]] = findFiles(Set(musicId)).map(_.get(musicId))
  def fetchUserPlaylists(userId: ULID): UIO[Seq[Playlist]]
  def findPlaylist(playlistId: ULID): UIO[Option[Playlist]]
  def fetchPlaylistContent(playlistIds: Set[ULID], page: Int = 0, pageLen: Int = 30): UIO[Map[ULID, Seq[Music]]]

object MusicPersist:
  case class Query(
      title: Option[Predef.String] = None,
      artist: Option[Predef.String] = None,
      album: Option[Predef.String] = None,
      genre: Option[Predef.String] = None
  )

  def get(musicId: ULID): ZIO[MusicPersist, Option[Nothing], Music] =
    ZIO.serviceWithZIO(_.get(musicId))

  def create(music: Music, file: File, cover: Option[FullStaticFile]): URIO[MusicPersist, Music] =
    ZIO.serviceWithZIO(_.create(music, file, cover))

  def update(music: Music): URIO[MusicPersist, Music] =
    ZIO.serviceWithZIO(_.update(music))

  def list(query: Query, page: Int = 0, pageLen: Int = 30): URIO[MusicPersist, Seq[Music]] =
    ZIO.serviceWithZIO(_.list(query, page, pageLen))

  def findFiles(musicIds: Set[ULID]): URIO[MusicPersist, Map[ULID, File]] =
    ZIO.serviceWithZIO(_.findFiles(musicIds))

  def findFile(musicId: ULID): URIO[MusicPersist, Option[File]] =
    ZIO.serviceWithZIO(_.findFile(musicId))

  def fetchUserPlaylists(userId: ULID): URIO[MusicPersist, Seq[Playlist]] =
    ZIO.serviceWithZIO(_.fetchUserPlaylists(userId))

  def findPlaylist(playlistId: ULID): URIO[MusicPersist, Option[Playlist]] =
    ZIO.serviceWithZIO(_.findPlaylist(playlistId))

  def fetchPlaylistContent(playlistIds: Set[ULID], page: Int = 0, pageLen: Int = 30): URIO[MusicPersist, Map[ULID, Seq[Music]]] =
    ZIO.serviceWithZIO(_.fetchPlaylistContent(playlistIds, page, pageLen))
