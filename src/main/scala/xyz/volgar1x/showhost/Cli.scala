package xyz.volgar1x.showhost

import zio.cli.{Args, HelpDoc}
import zio.http.URL

object Cli:
  def url(name: String = "url"): Args[URL] = Args
    .text(name)
    .mapOrFail: url =>
      URL.decode(url) match
        case Right(u)  => Right(u)
        case Left(exc) => Left(HelpDoc.p(exc.getMessage()))
