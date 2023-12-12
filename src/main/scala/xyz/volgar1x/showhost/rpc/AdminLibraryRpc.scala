package xyz.volgar1x.showhost.rpc

import io.getquill.JsonValue
import xyz.volgar1x.showhost.core.{File, FullStaticFile, Music, StaticFile, User}
import xyz.volgar1x.showhost.persist.{LibraryItemPersist, MusicPersist, StaticFilePersist, VideoPersist}
import xyz.volgar1x.showhost.util.indexBy
import xyz.volgar1x.showhost.{BaseEnvironment, Process, ShowhostConfig, ShowhostRpc, ULID, transport, ytdlp}

import zio.*

import java.nio.file.Files

object AdminLibraryRpc
    extends ShowhostRpc[BaseEnvironment & LibraryItemPersist & VideoPersist & MusicPersist & StaticFilePersist & Process]:
  import transport.AdminLibraryServiceGrpc.*
  import LibraryRpc.{music2proto, item2proto}

  private val updateMusic = METHOD_UPDATE_MUSIC.respondWithAuthOnlyRoleZIO(User.Role.admin): (msg, _) =>
    import transport.UpdateMusicResponse.*
    val status =
      for
        config <- ShowhostConfig()
        music  <- MusicPersist.get(ULID(msg.music.id)).orElseFail(Failure(1, Some("no such music")))
        cover  <- music.coverId.fold(ZIO.none)(id => StaticFilePersist.get(id).option)
        music2 <- MusicPersist.update(
          music.copy(
            title = msg.music.title,
            artists = JsonValue(msg.music.artists),
            album = msg.music.album,
            genre = msg.music.genre
          )
        )
      yield Success(music2proto(music2, config, cover))
    status.fold(Status.Failure(_), Status.Success(_)).map(transport.UpdateMusicResponse(_))

  private val addMusic = METHOD_ADD_MUSIC.respondWithAuthOnlyRoleZIO(User.Role.admin): (msg, _) =>
    import transport.AddMusicResponse.*
    val status =
      for
        config     <- ShowhostConfig()
        sourceUrl0 <- ZIO.succeed(msg.music.sourceUrl).someOrFail(Failure(1, Some("no source provided")))
        sourceUrl  <- ZIO.fromEither(http.URL.decode(sourceUrl0)).mapError(exc => Failure(2, Some(exc.getMessage())))
        fileId     <- ULID.random
        coverId    <- ULID.random
        music0 <- ULID.random.map(musicId =>
          Music(
            id = musicId,
            duration = 0,
            title = msg.music.title,
            artists = JsonValue(msg.music.artists),
            album = msg.music.album,
            genre = msg.music.genre,
            fileId = fileId,
            coverId = Some(coverId),
            sourceUrl = Some(sourceUrl.encode)
          )
        )
        ytdlpRes <- ytdlp
          .run(sourceUrl, config.audioWriteLocation.resolve(music0.id.value))
          .mapError(_ => Failure(3, Some("cannot download right now")))

        file = File(fileId, ytdlpRes.`type`.fullType, ytdlpRes.size, config.audioWriteLocation.relativize(ytdlpRes.path).toString(), None)

        cover <- ZIO
          .attemptBlockingIO(Files.readAllBytes(ytdlpRes.coverPath))
          .map(coverData =>
            val ext = ytdlpRes.coverType.fileExtensions.headOption.getOrElse("img")
            FullStaticFile(
              file = StaticFile(coverId, None, s".$ext", ytdlpRes.coverType.fullType, coverData.length),
              data = coverData
            )
          )
          .option

        music <- MusicPersist.create(music0.copy(duration = ytdlpRes.info.duration), file, cover)
      yield Success(
        music2proto(music, config, cover.map(_.file))
      )

    status.fold(Status.Failure(_), Status.Success(_)).map(transport.AddMusicResponse(_))

  private val newTvShows = METHOD_NEW_TV_SHOWS.respondWithAuthOnlyRoleZIO(User.Role.admin): (_, user) =>
    for
      config <- ShowhostConfig()
      items  <- LibraryItemPersist.newTvShows()
      videos <- VideoPersist.findByItem(items.map(_.id).toSet).map(_.groupBy(_.libraryItemId))
      files  <- VideoPersist.findFiles(videos.valuesIterator.flatten.map(_.id).toSet)
      static <- StaticFilePersist
        .getMany(items.iterator.flatMap(it => it.coverId ++ it.backdropId).toSet)
        .map(_.indexBy(_.id))
      history <- VideoPersist.fetchViewHistoryByItem(user.id, items.iterator.map(_.id).toSet)
    yield transport.NewTvShowsResponse(
      items.map: it =>
        val v = videos.get(it.id).getOrElse(Seq.empty)
        item2proto(
          it,
          user,
          config,
          it.coverId.flatMap(static.get),
          it.backdropId.flatMap(static.get),
          v,
          files,
          history.map(_.videoId).find(v.map(_.id).toSet)
        )
    )

  override val handlers = List(updateMusic, addMusic, newTvShows)
