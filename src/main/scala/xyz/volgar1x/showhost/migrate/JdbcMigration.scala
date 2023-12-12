package xyz.volgar1x.showhost.migrate

import zio.{Cause, RIO, ZIO, ZLayer}

import java.sql.{Connection, ResultSet, SQLException}
import javax.sql.DataSource

trait JdbcMigration extends Migration[Connection]:
  protected final def execute(sql: String): RIO[Connection, Unit] =
    JdbcMigration.execute(sql).unit

object JdbcMigration extends MigrationHandler[DataSource]:
  type UserEnv = Connection
  type Handler = MigrationHandler.Aux[DataSource, Connection]

  override def initialize =
    transact:
      execute("CREATE TABLE IF NOT EXISTS showhost_schema (version INTEGER)")
        *> queryInt("SELECT COUNT(*) FROM showhost_schema").flatMap:
          case 0 => execute("INSERT INTO showhost_schema VALUES (0)").unit
          case _ => ZIO.unit

  def query(sql: String): RIO[Connection, ResultSet] =
    ZIO.serviceWithZIO: (conn: Connection) =>
      val stmt = conn.createStatement()
      ZIO.attemptBlockingIO(stmt.executeQuery(sql)) <* ZIO.logDebug(s"Executed: ${sql}")

  def queryInt(sql: String): RIO[Connection, Int] =
    query(sql).mapAttempt: rset =>
      if (!rset.next()) throw IllegalStateException()
      rset.getInt(1)

  def execute(sql: String): RIO[Connection, Boolean] =
    ZIO.serviceWithZIO: (conn: Connection) =>
      val stmt = conn.createStatement()
      ZIO.attemptBlockingIO(stmt.execute(sql)) <* ZIO.logDebug(s"Executed: ${sql}")

  override def current = queryInt("SELECT version FROM showhost_schema")

  override def setCurrent(version: Int) = execute(s"UPDATE showhost_schema SET version=$version").unit

  override def transact[R, T](zio: => RIO[R & Connection, T]) =
    for
      conn <- ZIO.serviceWithZIO[DataSource](ds => ZIO.attemptBlocking(ds.getConnection()))
      autoCommit = conn.getAutoCommit()
      _          = conn.setAutoCommit(false)
      result <- zio
        .foldCauseZIO(
          cause => ZIO.attemptBlockingIO(conn.rollback()) *> ZIO.failCause(cause),
          result => ZIO.attemptBlockingIO(conn.commit()) *> ZIO.succeed(result)
        )
        .provideSomeLayer(ZLayer.succeed(conn))
    yield
      conn.setAutoCommit(autoCommit)
      result

  private def isSqlException(th: Throwable) =
    th.isInstanceOf[SQLException] || th.getClass.getName == "PSQLException"

  override def reportCause(cause: Cause[Any]) =
    ZIO.foldLeft(cause.failures ++ cause.defects)(())((_, x) =>
      x match
        case th: Throwable if isSqlException(th) =>
          val lines = th.getMessage().split(java.lang.System.lineSeparator()).map(_.trim())
          ZIO.foldLeft(lines)(())((_, line) => ZIO.logError(line))
        case _ => ZIO.logErrorCause("unknown error", cause)
    )
