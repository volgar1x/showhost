package xyz.volgar1x.showhost

import xyz.volgar1x.showhost.core.LibraryItem
import xyz.volgar1x.showhost.util.Bijection

import zio.http

package object tmdb:
  val location     = http.URL.Location.Absolute(http.Scheme.HTTPS, "www.themoviedb.org", http.Scheme.HTTPS.defaultPort)
  val apiLocation  = http.URL.Location.Absolute(http.Scheme.HTTPS, "api.tmdb.org", http.Scheme.HTTPS.defaultPort)
  val imgLocation  = http.URL.Location.Absolute(http.Scheme.HTTPS, "image.tmdb.org", http.Scheme.HTTPS.defaultPort)
  val coverSize    = "w500"
  val backdropSize = "w1280"

  given Bijection[LibraryItem.Type, String] = Bijection.simple(
    LibraryItem.Type.movies  -> "movie",
    LibraryItem.Type.tvShows -> "tv"
  )
