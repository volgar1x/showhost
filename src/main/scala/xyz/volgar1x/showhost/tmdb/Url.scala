package xyz.volgar1x.showhost.tmdb

import xyz.volgar1x.showhost.core.LibraryItem
import xyz.volgar1x.showhost.util.Bijection.{convertFrom, convertTo}

import zio.http.*

import scala.util.Try

object Url:
  def apply(itemType: LibraryItem.Type, tmdbId: String): URL =
    URL(Root / itemType.convertTo / tmdbId, location, QueryParams(), None)

  def unapply(url: URL): Option[(LibraryItem.Type, String)] = url match
    case URL(Root / itemType / itemId, `location`, _, _) =>
      Try(itemType.convertFrom).toOption.map((_, itemId))
    case _ => None

object ApiUrl:
  def apply(apiKey: String, itemType: LibraryItem.Type, tmdbId: String, more: String*): URL =
    URL(more.foldLeft(Root / "3" / itemType.convertTo / tmdbId)((acc, x) => acc / x), apiLocation, QueryParams("api_key" -> apiKey), None)

  def unapply(url: URL): Option[(LibraryItem.Type, String)] = url match
    case URL(Root / "3" / itemType / itemId, `apiLocation`, _, _) =>
      Try(itemType.convertFrom).toOption.map((_, itemId))
    case _ => None
