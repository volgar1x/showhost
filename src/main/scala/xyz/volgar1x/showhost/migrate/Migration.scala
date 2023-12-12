package xyz.volgar1x.showhost.migrate

import zio.*

trait Migration[R]:
  val version: Int
  def up: RIO[R, Unit]
  def down: RIO[R, Unit]

trait MigrationHandler[-Env]:
  type UserEnv
  def initialize: RIO[Env, Unit]
  def current: RIO[UserEnv, Int]
  def setCurrent(current: Int): RIO[UserEnv, Unit]
  def transact[R, T](zio: => RIO[UserEnv & R, T]): RIO[R & Env, T]
  def reportCause(cause: Cause[Any]): UIO[Unit]

object MigrationHandler:
  type Aux[R0, R1] = MigrationHandler[R0] { type UserEnv = R1 }
  def apply[R](implicit h: MigrationHandler[R]): MigrationHandler[R] = h

object Migration:
  def up[R0, R1](target: Option[Int], migrations: Migration[R1]*)(using MigrationHandler.Aux[R0, R1]): URIO[R0, Unit] =
    val h = summon[MigrationHandler.Aux[R0, R1]]
    (
      for
        _       <- h.initialize
        current <- h.transact(h.current)
        t  = target.getOrElse(migrations.last.version)
        ms = migrations.sortBy(_.version).dropWhile(_.version <= current).takeWhile(_.version <= t)
        _ <- ZIO.logDebug(s"migrating from ${current} to ${t} with migrations: ${ms}")
        _ <- ZIO.foldLeft(ms)(())((_, m) => h.transact(m.up *> h.setCurrent(m.version)))
      yield ()
    ).catchAllCause(h.reportCause)

  def down[R0, R1](target: Int, migrations: Migration[R1]*)(using MigrationHandler.Aux[R0, R1]): URIO[R0, Unit] =
    val h = summon[MigrationHandler.Aux[R0, R1]]
    (
      for
        _       <- h.initialize
        current <- h.transact(h.current)
        ms = migrations.sortBy(_.version).dropWhile(_.version <= target).takeWhile(_.version <= current)
        _ <- ZIO.logDebug(s"migrating from ${current} to ${target} with migrations: ${ms}")
        _ <- ZIO.foldLeft(ms.reverse)(())((_, m) => h.transact(m.down *> h.setCurrent(m.version - 1)))
      yield ()
    ).catchAllCause(h.reportCause)

  final case class Infos(currentVersion: Int)

  def infos[R](using MigrationHandler[R]): RIO[R, Infos] =
    val h = MigrationHandler[R]
    for currentVersion <- h.transact(h.current)
    yield Infos(currentVersion)
