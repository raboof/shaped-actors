package akkashaped

import scala.concurrent._
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.annotation.implicitNotFound

import shapeless._
import shapeless.HList._

import akka.actor._
import akka.pattern._
import akka.pattern.ask
import akka.util._

object Shaped {
  def respondWith[T](value: T) = Future.successful(value)

  @implicitNotFound("Implicit not found: Performer[${L}, ${I}, ${O}]. You requested an element of type ${I} => ${O}, but there is none in the HList ${L}.")
  trait Performer[L <: HList, I, O] extends DepFn2[L, I] with Serializable {
    type In = I
    type Out = O
  }

  object Performer {
    implicit def perform[H1, H2, T <: HList]: Performer[(H1 => H2) :: T, H1, H2] =
      new Performer[(H1 => H2) :: T, H1, H2] {
        def apply(l: (H1 => H2) :: T, i : H1) = l.head(i)
      }

    implicit def recurse[H, T <: HList, I, O](implicit p: Performer[T, I, O]): Performer[H :: T, I, O] =
      new Performer[H :: T, I, O] {
        def apply(l : H :: T, i: I) = p(l.tail, i)
      }
  }

  trait PfConverter[L <: HList] extends DepFn1[L] with Serializable {
    type Out = PartialFunction[Any, Unit]
  }
  object PfConverter {
    implicit def pfConv[H1, H2, T <: HList](implicit tailConv: PfConverter[T], tag: ClassTag[H1]): PfConverter[(H1 => H2) :: T] =
      new PfConverter[(H1 => H2) :: T] {
        def apply(l : (H1 => H2) :: T): PartialFunction[Any, Unit] = {
          val pf: PartialFunction[Any, Unit] = { case i: H1 => l.head(i); () }
          pf.orElse(tailConv(l.tail))
        }
      }
    implicit val emptyPfO: PfConverter[HNil.type] =
      new PfConverter[HNil.type] {
        def apply(n: HNil.type) = Map.empty[Any, Unit]
      }
    implicit val emptyPfC: PfConverter[HNil] =
      new PfConverter[HNil] {
        def apply(n: HNil) = Map.empty[Any, Unit]
      }

  }

  implicit def convertToPf[L <: HList](in: L)(implicit converter: PfConverter[L]) = converter(in)
}
trait Shaped[L <: HList] { self: Actor =>
  import Shaped._

  implicit val askTimeout: Timeout = 1 second

  def sask[I, O](in: I)(implicit performer: Performer[L, I, O], tag: ClassTag[O]): Future[O] =
    self.self.ask(in).mapTo[O]

  def become(newReceive: L)(implicit pfConverter: PfConverter[L]) =
    context.become(pfConverter(newReceive))

}
