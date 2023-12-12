package xyz.volgar1x.showhost.rpc

import xyz.volgar1x.showhost.core.{File, LibraryItem, Music, StaticFile, User, Video}
import xyz.volgar1x.showhost.persist.{LibraryItemPersist, MusicPersist, StaticFilePersist, VideoPersist}
import xyz.volgar1x.showhost.transport.*
import xyz.volgar1x.showhost.util.Bijection.*
import xyz.volgar1x.showhost.util.{Bijection, indexBy}
import xyz.volgar1x.showhost.{BaseEnvironment, ShowhostConfig, ShowhostRpc, ULID, transport}

import zio.*

import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

object LibraryRpc extends ShowhostRpc[BaseEnvironment & LibraryItemPersist & StaticFilePersist & VideoPersist & MusicPersist]:
  import LibraryServiceGrpc.*
  import LibraryItem.*

  private val listVideos = METHOD_LIST_VIDEOS.respondWithAuthZIO: (msg, user) =>
    import ListLibraryVideoResponse.*

    val filterItemType = msg.filterItemType match
      case itemTypes if itemTypes.nonEmpty => itemTypes.convertFrom[Seq]
      case _                               => LibraryItem.Type.values.toIndexedSeq
    val page    = msg.page.map(_ - 1).getOrElse(0)
    val pageLen = msg.pageLen.getOrElse(30)

    for
      config <- ShowhostConfig()
      items <- msg.sortKey.getOrElse(ListLibrarySortKey.DEFAULT) match
        case ListLibrarySortKey.DEFAULT         => LibraryItemPersist.list(filterItemType, page, pageLen)
        case ListLibrarySortKey.NEXT_RELEASE    => LibraryItemPersist.recent(filterItemType, page, pageLen)
        case ListLibrarySortKey.LAST_SEEN       => LibraryItemPersist.lastSeenBy(user.id, filterItemType, page, pageLen)
        case ListLibrarySortKey.Unrecognized(_) => ZIO.die(IllegalArgumentException())
      history <- VideoPersist.fetchViewHistoryByItem(user.id, items.iterator.map(_.id).toSet)
      videos  <- VideoPersist.findByItem(items.iterator.map(_.id).toSet).map(_.groupBy(_.libraryItemId))
      files   <- VideoPersist.findFiles(videos.valuesIterator.flatten.map(_.id).toSet)
      static <- StaticFilePersist
        .getMany(items.iterator.flatMap(it => it.coverId ++ it.backdropId).toSet)
        .map(_.indexBy(_.id))
    yield ListLibraryVideoResponse(
      items.map(it =>
        val v    = videos.getOrElse(it.id, Seq.empty)
        val vIds = v.iterator.map(_.id).toSet
        item2proto(
          it,
          user,
          config,
          it.coverId.flatMap(static.get),
          it.backdropId.flatMap(static.get),
          v,
          files,
          history.map(_.videoId).find(vIds)
        )
      )
    )

  private val getVideo = METHOD_GET_VIDEO.respondWithAuthZIO: (msg, user) =>
    import GetLibraryVideoRequest.*
    import GetLibraryVideoResponse.*
    val item =
      for
        config <- ShowhostConfig()
        item <- msg.request match
          case Request.ItemId(itemId) => LibraryItemPersist.get(ULID(itemId))
          case Request.LastPlayed(_) =>
            LibraryItemPersist.lastSeenBy(user.id, LibraryItem.Type.values.toIndexedSeq, pageLen = 1).map(_.headOption).some
          case Request.Empty => ZIO.die(IllegalArgumentException())
        history  <- VideoPersist.fetchViewHistoryByItem(user.id, Set(item.id))
        videos   <- VideoPersist.findByItem(Set(item.id))
        files    <- VideoPersist.findFiles(videos.iterator.map(_.id).toSet)
        cover    <- item.coverId.fold(ZIO.succeed(None))(StaticFilePersist.get(_).option)
        backdrop <- item.backdropId.fold(ZIO.succeed(None))(StaticFilePersist.get(_).option)
      yield item2proto(item, user, config, cover, backdrop, videos, files, history.headOption.map(_.videoId))

    item.option.map(GetLibraryVideoResponse(_))

  private val playVideo = METHOD_PLAY_VIDEO.respondWithAuthZIO: (msg, user) =>
    import PlayVideoResponse.*

    val status =
      for
        config      <- ShowhostConfig()
        video       <- VideoPersist.get(ULID(msg.id)).orElseFail(Failure(1, Some("no such video")))
        item        <- LibraryItemPersist.get(video.libraryItemId).orElseFail(Failure(2, Some("no such library item")))
        history     <- VideoPersist.fetchViewHistoryByItem(user.id, Set(item.id))
        otherVideos <- VideoPersist.findByItem(Set(item.id))
        files       <- VideoPersist.findFiles(Set(video.id))
        cover       <- item.coverId.fold(ZIO.succeed(None))(StaticFilePersist.get(_).option)
        backdrop    <- item.backdropId.fold(ZIO.succeed(None))(StaticFilePersist.get(_).option)
        viewSession <- VideoPersist.genViewSession(user.id, video.id)
      yield Success(
        videoUrl = (config.videoBase / video.id.value).encode,
        subtitleUrl = Some((config.subtitleBase / video.id.value).encode),
        item = item2proto(item, user, config, cover, backdrop, otherVideos, files, history.headOption.map(_.videoId)),
        video = video2proto(video, files.getOrElse(video.id, Seq.empty)),
        viewSessionId = viewSession.id.value,
        lastProgress = viewSession.progress
      )

    status.fold(Status.Failure(_), Status.Success(_)).map(PlayVideoResponse(_))

  private val listMusic = METHOD_LIST_MUSIC.respondWithAuthZIO: (msg, _) =>
    import ListMusicResponse.*

    val query = MusicPersist.Query(
      title = msg.query.title,
      artist = msg.query.artist,
      album = msg.query.album,
      genre = msg.query.genre
    )

    for
      config <- ShowhostConfig()
      musics <- MusicPersist.list(query, msg.page, msg.pageLen)
      covers <- StaticFilePersist.getMany(musics.flatMap(_.coverId).toSet).map(_.indexBy(_.id))
    yield ListMusicResponse().withSuccess(Success(musics.map(m => music2proto(m, config, m.coverId.flatMap(covers.get)))))

  override val handlers = List(listVideos, getVideo, playVideo, listMusic)

  def item2proto(
      item: LibraryItem,
      user: User,
      config: ShowhostConfig,
      cover: Option[StaticFile],
      backdrop: Option[StaticFile],
      videos: Seq[Video],
      files: Map[ULID, Seq[File]],
      lastViewedId: Option[ULID]
  ) =
    val nextEpisode =
      (item.nextReleaseMeta.map(_.value.seasonNumber) zip item.nextReleaseMeta.map(_.value.episodeNumber)).map(LibraryVideo.Episode.of)
    val lastEpisode = videos.flatMap(x => x.season zip x.episode).maxOption.map(LibraryVideo.Episode.of)
    LibraryVideo(
      id = item.id.value,
      videoType = item.itemType.convertTo,
      name = item.name(user.profileData.value.interfaceLang).getOrElse("noname"),
      summary = item.summary(user.profileData.value.interfaceLang),
      coverUrl = cover.map(c => (config.contentBase / c.filename).encode),
      backdropUrl = backdrop.map(b => (config.contentBase / b.filename).encode),
      firstReleased = Some(item.firstReleased.format(ISO_LOCAL_DATE)),
      lastReleased = Some(item.lastReleased.format(ISO_LOCAL_DATE)),
      myRatingScore = None, // TODO
      rating = item.ratings.value.map: r =>
        LibraryVideo.RatingSource(r.name, Some(r.logoUrl).filter(_.nonEmpty), Math.round(r.score)),
      directors = item.directors.value,
      screenwriters = item.screenwriters.value,
      producers = item.producers.value,
      genreId = Seq.empty, // TODO
      videos = videos
        .sortBy(v => (v.season, v.episode, v.id))
        .map: vi =>
          video2proto(vi, files.getOrElse(vi.id, Seq.empty)),
      lastViewedId = lastViewedId.map(_.value),
      nextRelease = item.nextRelease.map(_.format(ISO_LOCAL_DATE)),
      nextEpisode = nextEpisode,
      lastEpisode = lastEpisode
    )

  def video2proto(vi: Video, files: Seq[File]) =
    transport.Video(
      id = vi.id.value,
      name = vi.name,
      summary = vi.summary,
      season = vi.season,
      episode = vi.episode,
      released = vi.released.map(_.format(ISO_LOCAL_DATE)),
      durationS = vi.duration,
      availableAudioLanguage = files.filterNot(_.mimeType.startsWith("text")).flatMap(_.lang),
      availableSubtitleLanguage = files.filter(_.mimeType.startsWith("text")).flatMap(_.lang)
    )

  def music2proto(m: Music, config: ShowhostConfig, cover: Option[StaticFile]) =
    transport.Music(
      id = m.id.value,
      title = m.title,
      artists = m.artists.value,
      album = m.album,
      genre = m.genre,
      duration = m.duration,
      coverUrl = cover.map(c => (config.contentBase / c.filename).encode),
      sourceUrl = m.sourceUrl,
      audioUrl = (config.audioBase / m.id.value).encode
    )
