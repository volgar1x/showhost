package xyz.volgar1x.showhost.util

import zio.json.JsonEncoder
import zio.nio.file.Files
import zio.{IO, ZIO}

import java.io.IOException
import java.nio.file.Path

def writeJson[A](path: Path, value: A)(using encoder: JsonEncoder[A]): IO[IOException, Unit] =
  val path2 = zio.nio.file.Path.fromJava(path)
  ZIO.ifZIO(Files.exists(path2))(ZIO.logWarning(s"File already exists, overwriting: ${path}"), ZIO.unit)
    *> Files.writeLines(path2, List(encoder.encodeJson(value)))
