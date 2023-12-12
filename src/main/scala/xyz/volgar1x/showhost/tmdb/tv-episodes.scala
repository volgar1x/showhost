package xyz.volgar1x.showhost.tmdb

import xyz.volgar1x.showhost.ShowhostConfig
import xyz.volgar1x.showhost.core.LibraryItem

import zio.json.{DeriveJsonDecoder, JsonDecoder, SnakeCase, jsonMemberNames}
import zio.{ZIO, http}

import java.time.LocalDate

@jsonMemberNames(SnakeCase)
case class Episode(
    name: Option[String],
    overview: Option[String],
    airDate: Option[LocalDate]
)

object Episode:
  given JsonDecoder[Episode] = DeriveJsonDecoder.gen

def fetchEpisode(tmdbId: String, season: Int, episode: Int): ZIO[ShowhostConfig & http.Client, Error, Episode] =
  for
    apiKey <- ShowhostConfig.get(_.tmdbApiKey).someOrFail(InvalidConfig)
    requestUrl = ApiUrl(apiKey, LibraryItem.Type.tvShows, tmdbId, "season", season.toString(), "episode", episode.toString())
    response <- http.Client.request(http.Request.get(requestUrl)).mapError(ClientError(_))
    body     <- response.body.asString.mapError(ClientError(_))
    episode  <- ZIO.fromEither(JsonDecoder[Episode].decodeJson(body)).mapError(DecodeError(_))
  yield episode
