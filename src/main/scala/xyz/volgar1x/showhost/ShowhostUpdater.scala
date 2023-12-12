package xyz.volgar1x.showhost

import xyz.volgar1x.showhost.core.LibraryItem
import xyz.volgar1x.showhost.persist.LibraryItemPersist

import zio.stream.ZStream
import zio.{Clock, ZIO}

object ShowhostUpdater:
  val tv =
    for
      _     <- ZIO.logInfo("Starting updating TV shows...")
      start <- Clock.nanoTime
      count <- ZStream
        .iterate(0)(_ + 1)
        .mapZIO(page => LibraryItemPersist.list(Seq(LibraryItem.Type.tvShows), page, 4))
        .takeUntil(_.isEmpty)
        .flattenIterables
        .tap(item => ZIO.logDebug(s"Updating TV show ${item.name("en")}"))
        .flatMap: item =>
          item.externalUrl match
            case tmdb.Url(_, tmdbId)    => ZStream.fromZIO(tmdb.updateLibraryItem(item, tmdbId))
            case tmdb.ApiUrl(_, tmdbId) => ZStream.fromZIO(tmdb.updateLibraryItem(item, tmdbId))
            case _                      => ZStream.empty
        .runFoldZIO(0): (acc, item) =>
          LibraryItemPersist.update(item).as(acc + 1)
        .tapErrorCause(cause => ZIO.logErrorCause("Cannot update tv show", cause))
      end <- Clock.nanoTime
      elapsed = (end - start) / 1_000_000
      _ <- ZIO.logInfo(s"Updated $count TV shows in ${elapsed}ms!")
    yield ()
