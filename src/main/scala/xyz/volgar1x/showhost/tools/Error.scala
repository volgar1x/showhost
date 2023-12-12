package xyz.volgar1x.showhost.tools

import xyz.volgar1x.showhost.tmdb

import java.io.{IOException, PrintWriter, StringWriter}
import java.nio.file.Path

sealed trait Error:
  def describe: String

final case class IOError(cause: IOException) extends Error:
  override def describe =
    val writer = StringWriter()
    cause.printStackTrace(PrintWriter(writer))
    writer.toString()

final case class DecodeError(path: Path, message: String) extends Error:
  override def describe = s"Cannot decode ${path}: ${message}"

object NoSuchItemError extends Error:
  override def describe = "This item does not exist in current library"

object UnsupportedItemError extends Error:
  override def describe = "This item does not support library imports"

object NoEpisodeMatchError extends Error:
  override def describe = "This file is an tv episode but is not numbered correctly"

final case class TmdbError(error: tmdb.Error) extends Error:
  override def describe = s"TMDB error: ${error.describe}"

final case class ConfigError(message: String) extends Error:
  override def describe = s"Configuration error: ${message}"
