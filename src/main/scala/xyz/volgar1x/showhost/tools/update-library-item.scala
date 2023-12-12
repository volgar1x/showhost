package xyz.volgar1x.showhost.tools

import xyz.volgar1x.showhost.persist.{LibraryItemPersist, StaticFilePersist, VideoPersist}
import xyz.volgar1x.showhost.{ShowhostConfig, ULID, tmdb}

import zio.{Console, URIO, ZIO, http}
import zio.stream.ZStream
import xyz.volgar1x.showhost.core.LibraryItem
import scala.collection.immutable.ArraySeq

private def updateOne(
    item: LibraryItem
): ZIO[ShowhostConfig & http.Client & LibraryItemPersist & StaticFilePersist & VideoPersist, Error, Unit] =
  for
    tmdbId  <- ZIO.fromOption(tmdb.Url.unapply(item.externalUrl)).map(_._2).mapError(_ => UnsupportedItemError)
    newItem <- tmdb.updateLibraryItem(item, tmdbId).mapError(TmdbError(_))
    _       <- LibraryItemPersist.update(newItem)

    videos <- VideoPersist.findByItem(Set(item.id))
    _ <- ZIO.collectAllPar:
      videos.map: video =>
        video.season.zip(video.episode) match
          case None => ZIO.unit
          case Some(season, episode) =>
            for
              videoMeta <- tmdb.fetchEpisode(tmdbId, season, episode).mapError(TmdbError(_))
              _ <- VideoPersist.update(
                video.copy(
                  name = videoMeta.name.getOrElse(""),
                  summary = videoMeta.overview,
                  released = videoMeta.airDate
                )
              )
            yield ()
  yield ()

def updateLibraryItem(itemId: ULID): URIO[ShowhostConfig & http.Client & LibraryItemPersist & StaticFilePersist & VideoPersist, Unit] =
  LibraryItemPersist
    .get(itemId)
    .mapError(_ => NoSuchItemError)
    .flatMap(updateOne)
    .catchAll: exc =>
      Console.printLine(exc.describe).orDie

def updateAllLibrary(): URIO[ShowhostConfig & http.Client & LibraryItemPersist & StaticFilePersist & VideoPersist, Unit] =
  ZStream
    .iterate(0)(_ + 1)
    .mapZIO(page => LibraryItemPersist.list(ArraySeq.unsafeWrapArray(LibraryItem.Type.values), page = page, pageLen = 10))
    .takeUntil(_.isEmpty)
    .flattenIterables
    .runForeach(updateOne)
    .catchAll: exc =>
      Console.printLine(exc.describe).orDie
