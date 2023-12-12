package xyz.volgar1x.showhost

import java.nio.file.Path
import scala.collection.Factory

package object util:
  extension (self: String)
    def splitext: Option[(String, String)] =
      self.lastIndexOf('.') match
        case pos if pos >= 0 => Some(self.substring(0, pos), self.substring(pos))
        case _               => None

  extension (self: Path)
    def splitext: Option[(String, String)] =
      self.getFileName().toString().splitext

  extension [A](self: Iterable[A])
    def indexBy[K](by: A => K): Map[K, A] =
      self.foldLeft(Map.empty[K, A])((acc, x) => acc.updated(by(x), x))

    def *[B, Col <: [X] =>> Iterable[X]](other: Col[B])(using factory: Factory[(A, B), Col[(A, B)]]): Col[(A, B)] =
      self
        .foldLeft(factory.newBuilder): (builder, a) =>
          other.foldLeft(builder): (builder, b: B) =>
            builder.addOne(a, b)
        .result()

  extension [A](option: Option[A])
    def ++(other: Option[A]): List[A] =
      List(option, other).flatten
