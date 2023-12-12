package xyz.volgar1x.showhost.tmdb

import xyz.volgar1x.showhost.core.LibraryItem
import xyz.volgar1x.showhost.persist.StaticFilePersist
import xyz.volgar1x.showhost.{ShowhostConfig, ULID}

import zio.json.ast.Json
import zio.{ZIO, http}

private def requestLibraryItem(url: http.URL, language: Option[String]) =
  for
    response <- http.Client
      .request(http.Request.get(language.fold(url)(language => url.copy(queryParams = url.queryParams.add("language", language)))))
      .mapError(ClientError(_))

    body <- response.body.asString
      .mapError(ClientError(_))
      .flatMap(body => ZIO.fromEither(Json.decoder.decodeJson(body)).mapError(msg => DecodeError(msg)))
      .flatMap(json => ZIO.fromOption(json.asObject).orElseFail(DecodeError("cannot decode response body")))
  yield body

def updateLibraryItem(
    item: LibraryItem,
    tmdbId: String
): ZIO[ShowhostConfig & http.Client & StaticFilePersist, Error, LibraryItem] =
  for
    languages <- ShowhostConfig.get(_.libraryLanguages)

    apiKey <- ZIO.serviceWithZIO[ShowhostConfig](c => ZIO.fromOption(c.tmdbApiKey)).orElseFail(InvalidConfig)
    body   <- requestLibraryItem(ApiUrl(apiKey, item.itemType, tmdbId), None)
    translations <- ZIO
      .collectAllPar(
        languages.map(language => requestLibraryItem(ApiUrl(apiKey, item.itemType, tmdbId), Some(language)).map(language -> _))
      )
      .map(_.toMap)

    newItem           <- json2item(body, item.id, item.itemType, Url(item.itemType, tmdbId), translations)
    newItemWithImages <- updateItemImages(body, newItem.copy(coverId = item.coverId, backdropId = item.backdropId))
  yield newItemWithImages

def newLibraryItem(
    itemType: LibraryItem.Type,
    tmdbId: String
): ZIO[ShowhostConfig & http.Client & StaticFilePersist, Error, LibraryItem] =
  for
    languages <- ShowhostConfig.get(_.libraryLanguages)

    apiKey <- ZIO.serviceWithZIO[ShowhostConfig](c => ZIO.fromOption(c.tmdbApiKey)).orElseFail(InvalidConfig)
    body   <- requestLibraryItem(ApiUrl(apiKey, itemType, tmdbId), None)
    translations <- ZIO
      .collectAllPar(
        languages.map(language => requestLibraryItem(ApiUrl(apiKey, itemType, tmdbId), Some(language)).map(language -> _))
      )
      .map(_.toMap)

    itemId <- ULID.random

    newItem           <- json2item(body, itemId, itemType, Url(itemType, tmdbId), translations)
    newItemWithImages <- updateItemImages(body, newItem)
  yield newItemWithImages
