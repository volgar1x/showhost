package xyz.volgar1x.showhost

import xyz.volgar1x.showhost.persist.{MusicPersist, StaticFilePersist, VideoPersist}
import xyz.volgar1x.showhost.util.{ZioHttp, splitext}

import zio.*
import zio.http.*
import zio.nio.file.{Path => NioPath}

import java.nio.file.InvalidPathException

object ShowhostHttp:
  def routes = static ++ getVideo ++ getSubtitle ++ getAudio

  val static: HttpApp[StaticFilePersist, Nothing] = Http.collectZIO:
    case Method.GET -> "" /: "static" /: filename =>
      val result =
        for
          filenameParts <- ZIO.fromOption(filename.last.flatMap(_.splitext)).orElseFail(Status.NotFound)
          file <- StaticFilePersist
            .get(ULID(filenameParts._1))
            .orElse(StaticFilePersist.findByPath(filenameParts._1).some)
            .orElseFail(Status.NotFound)
          _    <- ZIO.cond(file.extension == filenameParts._2, {}, Status.NotFound)
          data <- StaticFilePersist.load(file.id).orDieWith(_ => IllegalStateException())
        yield Response(
          headers = Headers(
            Header.Custom("content-type", file.mimeType),
            Header.ContentDisposition.`inline`(file.filename),
            Header.CacheControl.Multiple(
              NonEmptyChunk(
                Header.CacheControl.Public,
                Header.CacheControl.MaxAge(604800)
              )
            )
          ),
          body = Body.fromChunk(data)
        )

      result.fold(Response(_), identity)

  val getVideo: HttpApp[VideoPersist & ShowhostConfig, Nothing] = Http.collectZIO:
    case req @ Method.GET -> Root / "v" / videoId =>
      val lang = req.url.queryParams.getOrElse("lang", None).headOption
      (for
        config <- ShowhostConfig()
        video  <- VideoPersist.get(ULID(videoId)).orElseFail(HttpError.NotFound(req.url.encode))
        files  <- VideoPersist.findFiles(Set(video.id)).map(_.getOrElse(video.id, Seq.empty))
        file <- ZIO
          .fromOption(files.find: f =>
            f.mimeType.startsWith("video/")
              && lang.forall(f.lang contains _))
          .orElseFail(HttpError.NotFound(req.url.encode))
        response <- config.videoLocation match
          case ContentLocation.Local(dir) =>
            ZioHttp
              .fromPath(NioPath.fromJava(dir) / file.localPath, file.mimeType)(req)
              .catchAllCause:
                case cause @ Cause.Fail(_, _) =>
                  ZIO.logErrorCause("Cannot send video", cause) *> ZIO.fail(HttpError.InternalServerError())
                case cause @ Cause.Die(_: InvalidPathException, _) =>
                  ZIO.logErrorCause("Cannot send video", cause) *> ZIO.fail(HttpError.InternalServerError())
                case cause =>
                  cause.dieOption.fold(ZIO.die(IllegalStateException()))(ZIO.die(_))
          case ContentLocation.Remote(url) =>
            val videoUrl = url.copy(
              path = url.path / "v" / videoId,
              queryParams = lang.foldLeft(url.queryParams)(_.add("lang", _))
            )
            ZIO.succeed(Response.seeOther(videoUrl))
          case ContentLocation.Nginx(dir) =>
            val sourcePath = NioPath.fromJava(dir) / file.localPath
            ZIO.succeed(
              Response(headers =
                Headers(
                  Header.Custom("content-type", file.mimeType),
                  Header.Custom("x-accel-redirect", ZioHttp.urlencode(sourcePath.toString))
                )
              )
            )
      yield response)
        .catchAll:
          case error => ZIO.succeed(error.toResponse)

  val getSubtitle: HttpApp[VideoPersist & ShowhostConfig, Nothing] = Http.collectZIO:
    case req @ Method.GET -> Root / "s" / videoId =>
      val lang = req.url.queryParams.getOrElse("lang", None).headOption
      (for
        config <- ShowhostConfig()
        video  <- VideoPersist.get(ULID(videoId)).orElseFail(HttpError.NotFound(req.url.encode))
        files  <- VideoPersist.findFiles(Set(video.id)).map(_.getOrElse(video.id, Seq.empty))
        file <- ZIO
          .fromOption(files.find: f =>
            f.mimeType.startsWith("text/")
              && lang.forall(f.lang contains _))
          .orElseFail(HttpError.NotFound(req.url.encode))
        response <- config.videoLocation match
          case ContentLocation.Local(videoLocation) =>
            ZioHttp
              .fromPath(NioPath.fromJava(videoLocation) / file.localPath, file.mimeType)(req)
              .tapErrorCause(cause => ZIO.logErrorCause("Cannot send subtitle", cause))
              .orElseFail(HttpError.InternalServerError())
          case ContentLocation.Remote(url) =>
            val subtitleUrl = url.copy(
              path = url.path / "s" / videoId,
              queryParams = lang.foldLeft(url.queryParams)(_.add("lang", _))
            )
            ZIO.succeed(Response.seeOther(subtitleUrl))
          case ContentLocation.Nginx(dir) =>
            val sourcePath = NioPath.fromJava(dir) / file.localPath
            ZIO.succeed(
              Response(headers =
                Headers(
                  Header.Custom("content-type", file.mimeType),
                  Header.Custom("x-accel-redirect", ZioHttp.urlencode(sourcePath.toString))
                )
              )
            )
      yield response)
        .catchAll:
          case error => ZIO.succeed(error.toResponse)

  val getAudio: HttpApp[MusicPersist & ShowhostConfig, Nothing] = Http.collectZIO:
    case req @ Method.GET -> Root / "a" / musicId =>
      (for
        config <- ShowhostConfig()
        music  <- MusicPersist.get(ULID(musicId)).orElseFail(HttpError.NotFound(req.url.encode))
        file   <- MusicPersist.findFiles(Set(music.id)).map(_.get(music.id)).someOrFail(HttpError.NotFound(req.url.encode))
        response <- config.audioLocation match
          case ContentLocation.Local(audioLocation) =>
            ZioHttp
              .fromPath(NioPath.fromJava(audioLocation) / file.localPath, file.mimeType)(req)
              .tapErrorCause(cause => ZIO.logErrorCause("Cannot send audio", cause))
              .orElseFail(HttpError.InternalServerError())

          case ContentLocation.Remote(url) =>
            val audioUrl = url.copy(path = url.path / "a" / musicId)
            ZIO.succeed(Response.seeOther(audioUrl))

          case ContentLocation.Nginx(dir) =>
            val sourcePath = NioPath.fromJava(dir) / file.localPath
            ZIO.succeed(
              Response(headers =
                Headers(
                  Header.Custom("content-type", file.mimeType),
                  Header.Custom("x-accel-redirect", ZioHttp.urlencode(sourcePath.toString))
                )
              )
            )
      yield response)
        .catchAll:
          case error => ZIO.succeed(error.toResponse)
