package xyz.volgar1x.showhost.tools

import xyz.volgar1x.showhost.core.{File, LibraryItem, Video}
import xyz.volgar1x.showhost.persist.{LibraryItemPersist, VideoPersist}
import xyz.volgar1x.showhost.{ShowhostConfig, ULID, tmdb}

import zio.json.JsonDecoder
import zio.nio.file.Files
import zio.{Console, URIO, ZIO, http}

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Files => JFiles, Path}

def importVideos(itemId: ULID, paths: Seq[Path]): URIO[ShowhostConfig & LibraryItemPersist & VideoPersist & http.Client, Unit] =
  (for
    item <- LibraryItemPersist.get(itemId).orElseFail(NoSuchItemError)
    _    <- ZIO.collectAll(paths.map(importPath(item, _)))
  yield ()).catchAll(err => Console.printLine(err.describe).ignoreLogged)

def importPath(item: LibraryItem, path: Path): ZIO[ShowhostConfig & VideoPersist & http.Client, Error, Unit] =
  Files
    .readAttributes[BasicFileAttributes](path)
    .mapError(IOError(_))
    .flatMap:
      case attrs if attrs.isDirectory() =>
        Files
          .list(path)
          .mapError(IOError(_))
          .runForeach(importPath(item, _))

      case attrs if attrs.isRegularFile() && path.getFileName().toString().endsWith("_showhost.json") =>
        importManifest(item, path)

      case _ =>
        ZIO.unit

def importManifest(item: LibraryItem, manifestPath: Path): ZIO[ShowhostConfig & VideoPersist & http.Client, Error, Unit] =
  inline def tvCoding(coding: Map[String, Int]) = coding.get("season").zip(coding.get("episode"))

  def createFile(config: ShowhostConfig, mf: ManifestFile) =
    val path = manifestPath.getParent().resolve(mf.filename)
    for
      fileId   <- ULID.random
      fileSize <- Files.size(path).mapError(IOError(_))
    yield File(
      id = fileId,
      mimeType = mf.mimeType,
      size = fileSize,
      localPath = config.videoOrigin.relativize(path).toString(),
      lang = mf.tags.get("language").orElse(mf.tags.get("lang")).flatMap(_.asString)
    )

  for
    config <- ShowhostConfig()
    tmdbId <- ZIO.succeed(tmdb.Url.unapply(item.externalUrl)).someOrFail(UnsupportedItemError).map(_._2)
    manifest <- ZIO
      .attemptBlockingIO(JFiles.readString(manifestPath))
      .mapError(IOError(_))
      .flatMap(contents => ZIO.fromEither(JsonDecoder[Manifest].decodeJson(contents)).mapError(DecodeError(manifestPath, _)))

    _ <- item.itemType match
      case LibraryItem.Type.movies =>
        for
          files <- ZIO.collectAll(manifest.files.map(createFile(config, _)))
          video <- ULID.random.map: videoId =>
            Video(
              id = videoId,
              libraryItemId = item.id,
              name = "",
              summary = None,
              season = None,
              episode = None,
              coverUrl = None,
              released = None,
              duration = manifest.files.head.duration
            )
          _ <- VideoPersist.create(video, files)
          _ <- Console.printLine(s"Imported ${item.name("en")}").orDie
        yield ()
      case LibraryItem.Type.tvShows =>
        ZIO.collectAll:
          manifest.files
            .groupBy(mf => tvCoding(mf.coding))
            .map:
              case (Some((season, episode)), mfs) =>
                for
                  meta  <- tmdb.fetchEpisode(tmdbId, season, episode).mapError(TmdbError(_))
                  files <- ZIO.collectAll(mfs.map(createFile(config, _)))
                  video <- ULID.random.map: videoId =>
                    Video(
                      id = videoId,
                      libraryItemId = item.id,
                      name = meta.name.getOrElse(String.format("S%02dE%02d", season, episode)),
                      summary = meta.overview,
                      season = Some(season),
                      episode = Some(episode),
                      coverUrl = None,
                      released = meta.airDate,
                      duration = mfs.head.duration
                    )
                  _ <- VideoPersist.create(video, files)
                  _ <- Console.printLine(s"Imported ${item.name("en")} episode ${video.name}").orDie
                yield ()

              case (None, files) =>
                ZIO.logError(s"These files cannot be added: ${files.map(_.filename)}")
  yield ()
