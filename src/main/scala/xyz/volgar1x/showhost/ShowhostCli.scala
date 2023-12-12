package xyz.volgar1x.showhost

import zio.*
import zio.cli.*
import zio.http.URL

import java.nio.file.Path

object ShowhostCli:
  sealed trait Subcommand
  object Subcommand:
    case class Start(
        listenAddr: Option[String],
        listenPort: Option[Int]
    ) extends Subcommand
    sealed trait Migrate                                          extends Subcommand
    final case class MigrateUp(version: Option[Int])              extends Migrate
    final case class MigrateDown(version: Int)                    extends Migrate
    object MigrateInfo                                            extends Migrate
    object AddUser                                                extends Subcommand
    object UpdateUserPassword                                     extends Subcommand
    final case class AddLibraryItem(url: URL)                     extends Subcommand
    final case class ConvertVideos(paths: Seq[Path])              extends Subcommand
    final case class ImportVideos(itemId: ULID, paths: Seq[Path]) extends Subcommand
    final case class RefreshVideo(itemId: ULID)                   extends Subcommand
    object RefreshVideos                                          extends Subcommand

  private val startCommand = Command(
    "start",
    Options.text("listenAddr").optional ++ Options.integer("listenPort").map(_.toInt).optional
  )
    .withHelp("Serve HTTP requests")
    .map:
      case (listenAddr, listenPort) => Subcommand.Start(listenAddr, listenPort)

  private val migrateCommand =
    Command("migrate")
      .withHelp("Run database migrations")
      .subcommands(
        Command("up", Options.integer("target").optional).map(t => Subcommand.MigrateUp(t.map(_.toInt))),
        Command("down", Options.integer("target")).map(t => Subcommand.MigrateDown(t.toInt)),
        Command("info").map(_ => Subcommand.MigrateInfo)
      )

  private val addUserCommand = Command("adduser")
    .withHelp("Add user to database")
    .as(Subcommand.AddUser)

  private val updateUserPasswordCommand = Command("moduser")
    .withHelp("Modify user password")
    .as(Subcommand.UpdateUserPassword)

  private val addLibraryItemCommand =
    Command("addlibraryitem", Options.none, Cli.url())
      .withHelp("Add library item to database")
      .map:
        case (url) => Subcommand.AddLibraryItem(url)

  private val importVideos = Command("importvideos", Options.none, Args.text("itemId") ++ Args.path("paths").+).map:
    case (itemId, paths) => Subcommand.ImportVideos(ULID(itemId), paths)

  private val convertVideos = Command("convertvideos", Options.none, Args.path("paths").+).map:
    case (paths) => Subcommand.ConvertVideos(paths)

  private val refreshVideo = Command("refreshvideo", Options.none, Args.text("itemId").map(ULID(_))).map:
    case (itemId) => Subcommand.RefreshVideo(itemId)

  private val refreshVideos = Command("refreshvideos").as(Subcommand.RefreshVideos)

  val command: Command[(Option[Path], Subcommand)] =
    Command("showhost", Options.file("config", Exists.Yes).optional).subcommands(
      startCommand,
      migrateCommand,
      addUserCommand,
      updateUserPasswordCommand,
      addLibraryItemCommand,
      importVideos,
      convertVideos,
      refreshVideo,
      refreshVideos
    )
