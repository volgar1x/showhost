package xyz.volgar1x.showhost.migrate

package object jdbc:
  def all = List(
    `0001_fix_library_items_next_release_meta_nulls`,
    `0002_add_library_items_seasons`
  )
