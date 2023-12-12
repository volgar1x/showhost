package xyz.volgar1x.showhost

import xyz.volgar1x.showhost.util.splitext

import java.nio.file.Path

package object tools:
  implicit def javaPath2zio(path: Path): zio.nio.file.Path = zio.nio.file.Path.fromJava(path)
  implicit def zioPath2java(path: zio.nio.file.Path): Path = Path.of(path.toString)

  private val videoCodingRegex =
    java.util.regex.Pattern.compile("S(?:eason)?(?<season>\\d+)\\s*E(?:pisode)?(?<episode>\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)

  def findVideoCodingInFilename(filename: String): Map[String, Int] =
    import scala.jdk.CollectionConverters.*
    val m = videoCodingRegex.matcher(filename.splitext.map(_._1).getOrElse(filename))
    if (m.find())
      m.namedGroups()
        .asScala
        .keys
        .map(group => group -> m.group(group).toInt)
        .toMap
    else Map.empty
