package xyz.volgar1x.showhost.migrate.jdbc

import xyz.volgar1x.showhost.migrate.JdbcMigration

import zio.ZIO

object `0001_fix_library_items_next_release_meta_nulls` extends JdbcMigration:
  override val version = 1

  override def up   = execute("UPDATE library_items SET next_release_meta=NULL WHERE json_typeof(next_release_meta)='null'")
  override def down = ZIO.unit
