package xyz.volgar1x.showhost

import zio.stream.ZStream
import zio.{IO, ZIO, ZLayer}

import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import scala.collection.Factory

trait Process:
  def exec(
      program: String,
      argv: Seq[String] = Nil,
      env: Option[Seq[(String, String)]] = None,
      cwd: Option[Path] = None
  ): IO[IOException, (String, String)]

  def execLive(
      program: String,
      argv: Seq[String] = Nil,
      env: Option[Seq[(String, String)]] = None,
      cwd: Option[Path] = None
  ): IO[IOException, Unit]

object Process:
  def default = ZLayer.succeed(DefaultProcess)

  def exec(
      program: String,
      argv: Seq[String] = Nil,
      env: Option[Seq[(String, String)]] = None,
      cwd: Option[Path] = None
  ): ZIO[Process, IOException, (String, String)] =
    ZIO.serviceWithZIO(_.exec(program, argv, env, cwd))

  def execLive(
      program: String,
      argv: Seq[String] = Nil,
      env: Option[Seq[(String, String)]] = None,
      cwd: Option[Path] = None
  ): ZIO[Process, IOException, Unit] =
    ZIO.serviceWithZIO(_.execLive(program, argv, env, cwd))

object DefaultProcess extends Process:

  def create(
      program: String,
      argv: Seq[String] = Nil,
      env: Option[Seq[(String, String)]] = None,
      cwd: Option[Path] = None,
      f: ProcessBuilder => ProcessBuilder = identity
  ): IO[IOException, java.lang.Process] =
    ZIO.logDebug(s"Executing command ${program} ${argv}")
      *> ZIO.attemptBlockingIO:
        val pb = new ProcessBuilder((program +: argv): _*)
        env.foreach(env => env.foreach((k, v) => pb.environment().put(k, v)))
        cwd.foreach(cwd => pb.directory(cwd.toFile()))
        f(pb).start()

  override def exec(
      program: String,
      argv: Seq[String] = Nil,
      env: Option[Seq[(String, String)]] = None,
      cwd: Option[Path] = None
  ): IO[IOException, (String, String)] =
    for
      process <- create(program, argv, env, cwd)
      _       <- ZIO.fromFutureJava(process.onExit()).refineToOrDie[IOException]
      _       <- ZIO.cond(process.exitValue() == 0, (), IOException(s"unexpected process exit value: ${process.exitValue()}"))
      outs <-
        ZStream.fromReader(process.inputReader(UTF_8)).runCollect.map(_.to(Factory.stringFactory))
          <&> ZStream.fromReader(process.errorReader(UTF_8)).runCollect.map(_.to(Factory.stringFactory))
    yield (outs._1, outs._2)

  override def execLive(program: String, argv: Seq[String], env: Option[Seq[(String, String)]], cwd: Option[Path]): IO[IOException, Unit] =
    for
      process <- create(program, argv, env, cwd, _.inheritIO())
      _       <- ZIO.fromFutureJava(process.onExit()).refineToOrDie[IOException]
      _       <- ZIO.cond(process.exitValue() == 0, (), IOException(s"unexpected process exit value: ${process.exitValue()}"))
    yield ()
