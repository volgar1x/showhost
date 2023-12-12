package xyz.volgar1x.showhost.migrate.jdbc

import xyz.volgar1x.showhost.migrate.JdbcMigration

object `0002_add_library_items_seasons` extends JdbcMigration:
  override val version: Int = 2

  override def up   = execute("ALTER TABLE library_items ADD COLUMN seasons JSON DEFAULT NULL")
  override def down = execute("ALTER TABLE library_items DROP COLUMN seasons")
