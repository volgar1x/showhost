package xyz.volgar1x.showhost.util

import scala.collection.Factory
import scala.collection.immutable.IntMap

trait Bijection[A, B] extends (A => B):
  self =>
  def inverse(b: B): A

  def swap: Bijection[B, A] = new Bijection[B, A]:
    override def apply(b: B): A   = self.inverse(b)
    override def inverse(a: A): B = self.apply(a)

object Bijection:
  trait IsEnum[A]:
    def ordinal(a: A): Int

  given scalaEnum[A <: scala.reflect.Enum]: IsEnum[A] with
    def ordinal(a: A): Int = a.ordinal

  given protobufEnum[A <: scalapb.GeneratedEnum]: IsEnum[A] with
    def ordinal(a: A): Int = a.value

  def apply[A, B](xs: (A, B)*)(implicit enumA: IsEnum[A], enumB: IsEnum[B]): Bijection[A, B] = new Bijection[A, B]:
    private val left  = xs.foldLeft(IntMap.newBuilder[B])((acc, x) => acc.addOne(enumA.ordinal(x._1), x._2)).result()
    private val right = xs.foldLeft(IntMap.newBuilder[A])((acc, x) => acc.addOne(enumB.ordinal(x._2), x._1)).result()

    override def apply(a: A): B   = left(enumA.ordinal(a))
    override def inverse(b: B): A = right(enumB.ordinal(b))

  def simple[A, B](xs: (A, B)*): Bijection[A, B] =
    new Bijection[A, B]:
      private val left              = xs.foldLeft(Map.newBuilder[A, B])(_.addOne(_)).result()
      private val right             = xs.foldLeft(Map.newBuilder[B, A])((builder, x) => builder.addOne(x._2, x._1)).result()
      override def apply(a: A): B   = left.apply(a)
      override def inverse(b: B): A = right.apply(b)

  def fromFunctions[A, B](l: A => B, r: B => A): Bijection[A, B] = new Bijection[A, B]:
    override def apply(a: A): B   = l(a)
    override def inverse(b: B): A = r(b)

  extension [A, B](self: A)(using bij: Bijection[A, B]) def convertTo: B = bij(self)

  extension [A, B](self: Iterable[A])(using bij: Bijection[A, B]) def convertTo[C](using f: Factory[B, C]): C = self.map(bij).to(f)

  extension [A, B](self: B)(using bij: Bijection[A, B]) def convertFrom: A = bij.inverse(self)

  extension [A, B](self: Iterable[B])(using bij: Bijection[A, B])
    def convertFrom[C[_]](using f: Factory[A, C[A]]): C[A] = self.map(bij.inverse).to(f)
