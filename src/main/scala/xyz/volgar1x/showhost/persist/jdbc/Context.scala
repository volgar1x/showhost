package xyz.volgar1x.showhost.persist.jdbc

import io.getquill.jdbczio.Quill
import io.getquill.{JsonValue, NamingStrategy, PluralizedTableNames, SnakeCase}

import zio.json.JsonEncoder

import javax.sql.DataSource

class ZioJdbcContext(ds: DataSource) extends Quill.Postgres(NamingStrategy(PluralizedTableNames, SnakeCase), ds):
  given [T](using encoderT: JsonEncoder[T]): JdbcEncoder[Option[JsonValue[T]]] =
    encoder(
      java.sql.Types.OTHER,
      (index, value, row) =>
        value match
          case None => row.setNull(index, java.sql.Types.OTHER)
          case Some(JsonValue(value)) =>
            val obj = org.postgresql.util.PGobject()
            obj.setType("json")
            obj.setValue(encoderT.encodeJson(value, None).toString())
            row.setObject(index, obj, java.sql.Types.OTHER)
    )
