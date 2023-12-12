package xyz.volgar1x.showhost.rpc

import xyz.volgar1x.showhost.core.Music
import xyz.volgar1x.showhost.persist.{MusicPersist, StaticFilePersist, UserPersist}
import xyz.volgar1x.showhost.util.indexBy
import xyz.volgar1x.showhost.{BaseEnvironment, ShowhostConfig, ShowhostRpc, ULID, transport}

import zio.*

object UserLibraryRpc extends ShowhostRpc[BaseEnvironment & MusicPersist & StaticFilePersist]:
  import transport.UserLibraryServiceGrpc.*
  import LibraryRpc.music2proto

  private val recordProgress = METHOD_RECORD_PROGRESS.respondWithAuthZIO: (msg, user) =>
    import transport.RecordProgressResponse.*

    for _ <- UserPersist.recordVideoProgress(user.id, ULID(msg.viewSessionId), msg.progress)
    yield transport.RecordProgressResponse().withSuccess(Success())

  private val fetchPlaylist = METHOD_FETCH_PLAYLIST.respondWithAuthZIO: (msg, user) =>
    import transport.FetchMusicPlaylistResponse.*

    for
      config <- ShowhostConfig()

      playlists <- msg.playlistId match
        case Some(playlistId) => MusicPersist.findPlaylist(ULID(playlistId)).map(_.toSeq)
        case None             => MusicPersist.fetchUserPlaylists(user.id)

      musics <-
        if msg.includeData.exists(identity)
        then MusicPersist.fetchPlaylistContent(playlists.iterator.map(_.id).toSet, msg.page.getOrElse(0), msg.pageLen.getOrElse(30))
        else ZIO.succeed(Map.empty[ULID, Seq[Music]])

      covers <- StaticFilePersist.getMany(musics.view.valuesIterator.flatten.flatMap(_.coverId).toSet).map(_.indexBy(_.id))
    yield transport
      .FetchMusicPlaylistResponse()
      .withSuccess:
        Success(
          playlists.map: pl =>
            val playlistMusics = musics.getOrElse(pl.id, Seq.empty)
            Playlist(
              id = pl.id.value,
              name = pl.name,
              musics = playlistMusics.map(m => music2proto(m, config, m.coverId.flatMap(covers.get))),
              musicCount = playlistMusics.size
            )
        )

  override val handlers = List(recordProgress, fetchPlaylist)
