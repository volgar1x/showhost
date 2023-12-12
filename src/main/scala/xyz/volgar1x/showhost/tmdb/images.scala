package xyz.volgar1x.showhost.tmdb

import xyz.volgar1x.showhost.ULID
import xyz.volgar1x.showhost.core.{LibraryItem, StaticFile}
import xyz.volgar1x.showhost.persist.StaticFilePersist

import zio.json.ast.Json
import zio.{ZIO, http}

def fetchImage(path: String, size: String): ZIO[http.Client & StaticFilePersist, Error, ULID] =
  import zio.http.*
  val url     = URL(Root / "t" / "p" / s"${size}${path}", imgLocation, QueryParams.empty, None)
  val request = Request(Body.empty, Headers.empty, Method.GET, url, Version.`HTTP/1.1`, None)
  for
    response <- Client.request(request).mapError(ClientError(_))
    body     <- response.body.asChunk.orElseFail(DecodeError("cannot download image"))
    mediaType <- ZIO
      .fromOption(response.header(Header.ContentType))
      .orElseFail(DecodeError("unexpected image content type"))
      .map(_.mediaType)
      .filterOrFail(_.mainType == "image")(DecodeError("unexpected image content type"))

    id <- ULID.random
    meta = StaticFile(id, None, mediaType.fileExtensions.headOption.map(ext => s".$ext").getOrElse(""), mediaType.fullType, body.size)
    _ <- StaticFilePersist.upload(meta, body)
  yield id

def updateItemImages(body: Json.Obj, item: LibraryItem): ZIO[http.Client & StaticFilePersist, Error, LibraryItem] =
  val cover = ZIO.fromOption(item.coverId) <>
    ZIO
      .fromOption(body.get("poster_path").flatMap(_.asString))
      .flatMap(fetchImage(_, coverSize))

  val backdrop = ZIO.fromOption(item.backdropId) <>
    ZIO
      .fromOption(body.get("backdrop_path").flatMap(_.asString))
      .flatMap(fetchImage(_, backdropSize))

  for imgs <- cover.option <&> backdrop.option
  yield item.copy(coverId = imgs._1, backdropId = imgs._2)
