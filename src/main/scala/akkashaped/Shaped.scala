package akkashaped

import scala.concurrent._
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.annotation.implicitNotFound

import shapeless._
import shapeless.HList._

import akka.actor._
import akka.pattern.{ pipe, PipeableFuture }
import akka.pattern.ask
import akka.util._

object Shaped {

}
trait Shaped[L <: HList] { self: Actor =>
  import Shaped._

  implicit val askTimeout: Timeout = 1 second

  trait PfConverter[L <: HList] extends DepFn1[L] with Serializable {
    type Out = PartialFunction[Any, Unit]
  }

  object PfConverter {
    implicit def pfConv[H1, H2, T <: HList](implicit tailConv: PfConverter[T], tag: ClassTag[H1]): PfConverter[(H1 => Future[H2]) :: T] =
      new PfConverter[(H1 => Future[H2]) :: T] {
        def apply(l: (H1 => Future[H2]) :: T): PartialFunction[Any, Unit] = {
          val pf: PartialFunction[Any, Unit] = {
            case i: H1 =>
              implicit val ec: ExecutionContext = self.context.dispatcher
              val result: PipeableFuture[H2] = l.head(i)
              result.pipeTo(sender())
              ()
          }
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

  implicit def convertToPf(in: L)(implicit converter: PfConverter[L]) = converter(in)

  def respondWith[T](value: T) = Future.successful(value)

  def become(newReceive: L)(implicit pfConverter: PfConverter[L]) =
    context.become(pfConverter(newReceive))

}
