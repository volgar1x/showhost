package xyz.volgar1x.showhost.tools

import xyz.volgar1x.showhost.persist.{LibraryItemPersist, StaticFilePersist}
import xyz.volgar1x.showhost.{ShowhostConfig, tmdb}

import zio.{Console, URIO, ZIO, http}

def createLibraryItem(url: http.URL): URIO[ShowhostConfig & http.Client & LibraryItemPersist & StaticFilePersist, Unit] =
  (for
    params <- ZIO.succeed(tmdb.Url.unapply(url)).someOrFail(UnsupportedItemError)
    item   <- tmdb.newLibraryItem(params._1, params._2).mapError(TmdbError(_))
    _      <- LibraryItemPersist.create(item)
    _      <- Console.printLine(s"Created item ${item.id.value} with titles: ${item.nameI18n}").orDie
  yield ()).catchAll: error =>
    Console.printLine(error.describe).orDie
