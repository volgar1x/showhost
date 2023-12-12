package xyz.volgar1x.showhost

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import xyz.volgar1x.showhost.crypto.SecureToken
import xyz.volgar1x.showhost.migrate.{JdbcMigration, Migration}
import xyz.volgar1x.showhost.persist.UserPersist
import xyz.volgar1x.showhost.persist.jdbc.{
  JdbcLibraryItemPersist,
  JdbcMusicPersist,
  JdbcStaticFilePersist,
  JdbcUserPersist,
  JdbcVideoPersist
}
import xyz.volgar1x.showhost.rpc.{AdminLibraryRpc, LibraryRpc, UserAuthRpc, UserLibraryRpc, UserRpc}
import xyz.volgar1x.showhost.util.delayUntilNextTime

import zio.*
import zio.cli.*
import zio.cli.figlet.FigFont
import zio.http.*
import zio.logging.backend.SLF4J

import java.net.InetSocketAddress
import java.time.LocalTime
import javax.sql.DataSource

object ShowhostApp extends ZIOCliDefault:
  import ShowhostCli.Subcommand

  override val bootstrap = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  // http {{{
  private def rpc     = UserAuthRpc ++ UserRpc ++ LibraryRpc ++ UserLibraryRpc ++ AdminLibraryRpc
  private def httpRpc = ShowhostRpc.httpV2(rpc)
  private def heartbeat = Http.collect:
    case Method.HEAD -> Root / "_heartbeat" => Response.ok

  private def httpApp =
    (heartbeat ++ httpRpc ++ ShowhostHttp.routes)
      @@ RequestHandlerMiddlewares.debug
      @@ HttpAppMiddleware.cors()
  // }}}

  private def loadConfig(path: Option[java.nio.file.Path]) =
    path.fold(ShowhostConfig.fromEnv() orElse ShowhostConfig.fromClasspath)(ShowhostConfig.read(_))

  override def cliApp = CliApp.make(
    name = "showhost",
    version = "0.1.0",
    summary = HelpDoc.Span.text("ShowHost backend executable"),
    command = ShowhostCli.command
  ):
    case (configPath, Subcommand.Start(listenAddr, listenPort)) =>
      for
        _ <- Console.printLine(FigFont.Default.render("ShowHost"))
        _ <- ZIO.logInfo("Server starting...")

        config <- loadConfig(configPath)

        bindAddress = InetSocketAddress(
          listenAddr.orElse(config.listenAddr).getOrElse("127.0.0.1"),
          listenPort.orElse(config.listenPort).getOrElse(8080)
        )

        dataSource <- ZIO.attempt:
          val c = HikariConfig()
          c.setDataSourceClassName(config.dataSourceClassName)
          for ((k, v) <- config.dataSource)
            c.addDataSourceProperty(k, v)
          HikariDataSource(c)

        daemons <- ZIO
          .collectAllPar(
            Seq(
              Server.serve(httpApp),
              ShowhostUpdater.tv
                .delayUntilNextTime(LocalTime.MIDNIGHT)
                .repeat(Schedule.hourOfDay(0) ++ Schedule.minuteOfHour(0) ++ Schedule.secondOfMinute(0))
            )
          )
          .provideSome[Environment](
            ZLayer.succeed[Option[SecureToken]](None),
            ZLayer.succeed(config),
            ZLayer.succeed(dataSource),
            Server.defaultWith(_.copy(address = bindAddress)),
            Client.default,
            JdbcUserPersist.layer,
            JdbcLibraryItemPersist.layer,
            JdbcStaticFilePersist.layer,
            JdbcVideoPersist.layer,
            JdbcMusicPersist.layer,
            Process.default
          )
          .fork

        _ <- Scope.addFinalizer(ZIO.logInfo("Server has stopped"))
        _ <- ZIO.logInfo(s"Server started on $bindAddress")
        _ <- daemons.await.rethrow.race(Console.readLine("Press [Enter] to exit\n") <> ZIO.never)
        _ <- Console.printLine("Exiting...") <> ZIO.unit
      yield ExitCode.success

    case (configPath, cmd: Subcommand.Migrate) =>
      given JdbcMigration.Handler = JdbcMigration
      val migrations              = xyz.volgar1x.showhost.migrate.jdbc.all
      for
        config <- loadConfig(configPath)

        dataSource <- ZIO.attempt:
          val c = HikariConfig()
          c.setDataSourceClassName(config.dataSourceClassName)
          for ((k, v) <- config.dataSource)
            c.addDataSourceProperty(k, v)
          HikariDataSource(c)

        _ <-
          cmd match
            case Subcommand.MigrateUp(version) =>
              Migration
                .up(version, migrations: _*)
                .provideSomeLayer(ZLayer.succeed(dataSource))
            case Subcommand.MigrateDown(version) =>
              Migration
                .down(version, migrations: _*)
                .provideSomeLayer(ZLayer.succeed(dataSource))
            case Subcommand.MigrateInfo =>
              for
                infos <- Migration.infos.provideSomeLayer(ZLayer.succeed(dataSource))
                _     <- ZIO.logInfo(infos.toString())
              yield ()
      yield ExitCode.success

    case (configPath, Subcommand.ConvertVideos(paths)) =>
      for
        config <- loadConfig(configPath)
        _ <-
          tools
            .convertVideos(paths)
            .provideSomeLayer(ZLayer.succeed(config))
            .provideSomeLayer(Process.default)
            .catchAllCause:
              case cause => ZIO.logErrorCause("Cannot convert videos", cause)
      yield ExitCode.success

    case (configPath, Subcommand.ImportVideos(itemId, paths)) =>
      for
        config <- loadConfig(configPath)
        dataSource <- ZIO.attempt:
          val c = HikariConfig()
          c.setDataSourceClassName(config.dataSourceClassName)
          for ((k, v) <- config.dataSource)
            c.addDataSourceProperty(k, v)
          HikariDataSource(c)
        _ <-
          tools
            .importVideos(itemId, paths)
            .provide(
              ZLayer.succeed(config),
              ZLayer.succeed(dataSource),
              Client.default,
              JdbcLibraryItemPersist.layer,
              JdbcVideoPersist.layer
            )
            .catchAllCause:
              case cause => ZIO.logErrorCause("Cannot convert videos", cause)
      yield ExitCode.success

    case (configPath, Subcommand.AddLibraryItem(url)) =>
      for
        config <- loadConfig(configPath)
        dataSource <- ZIO.attempt:
          val c = HikariConfig()
          c.setDataSourceClassName(config.dataSourceClassName)
          for ((k, v) <- config.dataSource)
            c.addDataSourceProperty(k, v)
          HikariDataSource(c)
        _ <- tools
          .createLibraryItem(url)
          .provide(
            ZLayer.succeed(config),
            ZLayer.succeed(dataSource),
            Client.default,
            JdbcLibraryItemPersist.layer,
            JdbcStaticFilePersist.layer
          )
      yield ExitCode.success

    case (configPath, Subcommand.AddUser) =>
      for
        config <- loadConfig(configPath)
        dataSource <- ZIO.attempt:
          val c = HikariConfig()
          c.setDataSourceClassName(config.dataSourceClassName)
          for ((k, v) <- config.dataSource)
            c.addDataSourceProperty(k, v)
          HikariDataSource(c)
        _ <- tools
          .addUser()
          .provide(
            ZLayer.succeed(dataSource),
            JdbcUserPersist.layer
          )
      yield ExitCode.success

    case (configPath, Subcommand.UpdateUserPassword) =>
      for
        config <- loadConfig(configPath)
        dataSource <- ZIO.attempt:
          val c = HikariConfig()
          c.setDataSourceClassName(config.dataSourceClassName)
          for ((k, v) <- config.dataSource)
            c.addDataSourceProperty(k, v)
          HikariDataSource(c)
        _ <- tools
          .updateUserPassword()
          .provide(
            ZLayer.succeed(dataSource),
            JdbcUserPersist.layer
          )
      yield ExitCode.success

    case (configPath, Subcommand.RefreshVideo(itemId)) =>
      for
        config <- loadConfig(configPath)
        dataSource <- ZIO.attempt:
          val c = HikariConfig()
          c.setDataSourceClassName(config.dataSourceClassName)
          for ((k, v) <- config.dataSource)
            c.addDataSourceProperty(k, v)
          HikariDataSource(c)
        _ <- tools
          .updateLibraryItem(itemId)
          .provide(
            ZLayer.succeed(config),
            ZLayer.succeed(dataSource),
            Client.default,
            JdbcLibraryItemPersist.layer,
            JdbcStaticFilePersist.layer,
            JdbcVideoPersist.layer
          )
      yield ExitCode.success

    case (configPath, Subcommand.RefreshVideos) =>
      for
        config <- loadConfig(configPath)
        dataSource <- ZIO.attempt:
          val c = HikariConfig()
          c.setDataSourceClassName(config.dataSourceClassName)
          for ((k, v) <- config.dataSource)
            c.addDataSourceProperty(k, v)
          HikariDataSource(c)
        _ <- tools
          .updateAllLibrary()
          .provide(
            ZLayer.succeed(config),
            ZLayer.succeed(dataSource),
            Client.default,
            JdbcLibraryItemPersist.layer,
            JdbcStaticFilePersist.layer,
            JdbcVideoPersist.layer
          )
      yield ExitCode.success

  extension [R, E, T](self: ZIO[R, Nothing, Exit[E, T]])
    def rethrow: ZIO[R, E, T] = self.flatMap:
      case Exit.Success(result) => ZIO.succeed(result)
      case Exit.Failure(cause)  => ZIO.failCause(cause)
