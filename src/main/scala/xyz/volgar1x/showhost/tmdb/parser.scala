package xyz.volgar1x.showhost.tmdb

import io.getquill.JsonValue
import xyz.volgar1x.showhost.ULID
import xyz.volgar1x.showhost.core.LibraryItem

import zio.json.ast.Json
import zio.{ZIO, http}

import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_DATE
import zio.json.JsonDecoder

private def optionCollectAll[Col <: [X] =>> Iterable[X], A](
    options: Col[Option[A]]
)(using factory: collection.Factory[A, Col[A]]): Option[Col[A]] =
  if options.nonEmpty && options.forall(_.isDefined)
  then Some(factory.fromSpecific(options.map(_.get)))
  else None

def json2item(
    body: Json.Obj,
    itemId: ULID,
    itemType: LibraryItem.Type,
    tmdbUrl: http.URL,
    translations: Map[String, Json.Obj]
): ZIO[Any, Error, LibraryItem] =
  ZIO
    .fromOption:
      for
        names <- optionCollectAll(
          translations.map((language, body) => body.get("title").orElse(body.get("name")).flatMap(_.asString).map(language -> _))
        ).map(_.toMap)
        summaries = translations.view
          .flatMap((language, body) => body.get("overview").flatMap(_.asString).map(language -> _))
          .toMap
        firstReleased <- itemType match
          case LibraryItem.Type.movies  => body.get("release_date").flatMap(_.asString)
          case LibraryItem.Type.tvShows => body.get("first_air_date").flatMap(_.asString)
        lastReleased <- itemType match
          case LibraryItem.Type.movies  => body.get("release_date").flatMap(_.asString)
          case LibraryItem.Type.tvShows => body.get("last_air_date").flatMap(_.asString)
      yield
        val producers = body.get("production_companies").flatMap(_.asArray).map(_.flatMap(_.asString).toList).getOrElse(Nil)
        val directors = itemType match
          case LibraryItem.Type.movies  => Nil
          case LibraryItem.Type.tvShows => body.get("created_by").flatMap(_.asArray).map(_.flatMap(_.asString).toList).getOrElse(Nil)
        val screenwriters = itemType match
          case LibraryItem.Type.movies  => Nil
          case LibraryItem.Type.tvShows => body.get("created_by").flatMap(_.asArray).map(_.flatMap(_.asString).toList).getOrElse(Nil)

        val (nextRelease, nextReleaseMeta) = itemType match
          case LibraryItem.Type.movies => (None, None)
          case LibraryItem.Type.tvShows =>
            body
              .get("next_episode_to_air")
              .flatMap(_.asObject)
              .map: obj =>
                (
                  obj.get("air_date").flatMap(_.asString).map(LocalDate.parse(_, ISO_DATE)),
                  LibraryItem.releaseMetaCodec.decoder.fromJsonAST(obj).toOption
                )
              .getOrElse((None, None))

        LibraryItem(
          id = itemId,
          itemType = itemType,
          firstReleased = LocalDate.parse(firstReleased, ISO_DATE),
          lastReleased = LocalDate.parse(lastReleased, ISO_DATE),
          ratings = JsonValue(
            List(
              LibraryItem.Rating("tmdb", "", body.get("vote_average").flatMap(_.asNumber).map(_.value.floatValue()).getOrElse(0f))
            )
          ),
          producers = JsonValue(producers),
          directors = JsonValue(directors),
          screenwriters = JsonValue(screenwriters),
          externalUrl = tmdbUrl,
          nameI18n = JsonValue(names),
          summaryI18n = JsonValue(summaries),
          coverId = None,
          backdropId = None,
          nextRelease = nextRelease,
          nextReleaseMeta = nextReleaseMeta.map(JsonValue(_)),
          seasons = Option
            .when(itemType == LibraryItem.Type.tvShows)(body)
            .flatMap(_.get("seasons"))
            .flatMap(JsonDecoder[Seq[LibraryItem.SeasonMeta]].fromJsonAST(_).toOption)
            .map(JsonValue(_))
        )
    .orElseFail(DecodeError("cannot decode response body"))
