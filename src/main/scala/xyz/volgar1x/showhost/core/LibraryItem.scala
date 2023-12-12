package xyz.volgar1x.showhost.core

import io.getquill.{JsonValue, MappedEncoding}
import xyz.volgar1x.showhost.ULID
import xyz.volgar1x.showhost.transport.LibraryVideoType
import xyz.volgar1x.showhost.util.Bijection

import zio.http.URL
import zio.json.{DeriveJsonCodec, JsonCodec, SnakeCase, jsonMemberNames}

import java.time.LocalDate

case class LibraryItem(
    id: ULID,
    itemType: LibraryItem.Type,
    firstReleased: LocalDate,
    lastReleased: LocalDate,
    ratings: JsonValue[Seq[LibraryItem.Rating]],
    producers: JsonValue[Seq[String]],
    directors: JsonValue[Seq[String]],
    screenwriters: JsonValue[Seq[String]],
    externalUrl: URL,
    nameI18n: JsonValue[Map[String, String]],
    summaryI18n: JsonValue[Map[String, String]],
    coverId: Option[ULID],
    backdropId: Option[ULID],
    nextRelease: Option[LocalDate],
    nextReleaseMeta: Option[JsonValue[LibraryItem.ReleaseMeta]],
    seasons: Option[JsonValue[Seq[LibraryItem.SeasonMeta]]]
) {
  def name(lang: String): Option[String] =
    nameI18n.value.get(lang).orElse(nameI18n.value.get(LibraryItem.defaultLanguage))

  def summary(lang: String): Option[String] =
    summaryI18n.value.get(lang).orElse(summaryI18n.value.get(LibraryItem.defaultLanguage))
}

object LibraryItem:
  enum Type:
    case movies, tvShows

  @jsonMemberNames(SnakeCase)
  case class Rating(name: String, logoUrl: String, score: Float)

  @jsonMemberNames(SnakeCase)
  case class ReleaseMeta(
      name: String,
      overview: String,
      airDate: LocalDate,
      episodeNumber: Int,
      seasonNumber: Int
  )

  @jsonMemberNames(SnakeCase)
  case class SeasonMeta(
      seasonNumber: Int,
      episodeCount: Int,
      airDate: LocalDate,
      name: String,
      overview: String
  )

  val defaultLanguage: String = "en"

  implicit val typeEncoding: MappedEncoding[Type, Int] = MappedEncoding(_.ordinal)
  implicit val typeDecoding: MappedEncoding[Int, Type] = MappedEncoding(Type.fromOrdinal)

  implicit val urlEncoding: MappedEncoding[URL, String] = MappedEncoding(_.encode)
  implicit val urlDecoding: MappedEncoding[String, URL] = MappedEncoding(URL.decode(_).getOrElse(throw IllegalArgumentException()))

  implicit val protobufType: Bijection[Type, LibraryVideoType] = Bijection(
    Type.movies  -> LibraryVideoType.MOVIE,
    Type.tvShows -> LibraryVideoType.TVSHOW
  )

  implicit val ratingCodec: JsonCodec[Rating]           = DeriveJsonCodec.gen
  implicit val releaseMetaCodec: JsonCodec[ReleaseMeta] = DeriveJsonCodec.gen
  implicit val seasonMetaCodec: JsonCodec[SeasonMeta]   = DeriveJsonCodec.gen
