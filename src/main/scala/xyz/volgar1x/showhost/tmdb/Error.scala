package xyz.volgar1x.showhost.tmdb

import java.io.{PrintWriter, StringWriter}

sealed trait Error:
  def describe: String

case object InvalidConfig extends Error:
  override def describe = "invalid config"

case class ClientError(th: Throwable) extends Error:
  override def describe =
    val writer = StringWriter()
    th.printStackTrace(PrintWriter(writer))
    writer.toString()

case class DecodeError(error: String) extends Error:
  override def describe = error
