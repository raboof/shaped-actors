package akkashaped

import scala.concurrent.{ Future, ExecutionContext }
import scala.annotation.implicitNotFound

import shapeless._
import shapeless.HList._
import shapeless.DepFn2

import akka.actor.{ Actor, ActorRef, Props, ActorRefFactory }
import akka.pattern.{ ask, AskableActorRef }
import akka.util.Timeout

class ShapedRef[L <: HList](ref: ActorRef) {
  import ShapedRef._

  def ask[I, O](in: I)(implicit timeout: Timeout, sender: ActorRef, performer: Performer[L, I, O]): O = {
    val askable: AskableActorRef = ref;
    askable.ask(in).asInstanceOf[O]
  }

  def tell[I](in: I)(implicit sender: ActorRef, performer: Performer[L, I, Unit]): Unit = ref ! in

  def retryAfter[I, O](in: I, future: Future[_])(implicit timeout: Timeout, sender: ActorRef, performer: Performer[L, I, O], ec: ExecutionContext): O = {
    future.flatMap { value =>
      ref ! value
      val askable: AskableActorRef = ref;
      askable.ask(in).asInstanceOf[Future[_]]
    }.asInstanceOf[O]
  }

}

object ShapedRef {
  /** Start a new actor of a given shape */
  def actorOf[T <: HList](creator: => Actor with Shaped[T])(implicit factory: ActorRefFactory): ShapedRef[T] =
    new ShapedRef(factory.actorOf(Props(creator)))

  /** Wrap an existing shaped actor */
  def wrap[T <: HList](actor: Actor with Shaped[T]): ShapedRef[T] = new ShapedRef(actor.self)

  @implicitNotFound("Implicit not found: Performer[${L}, ${I}, ${O}]. You requested an element of type ${I} => ${O}, but there is none in the HList ${L}.")
  trait Performer[L <: HList, I, O] extends DepFn2[L, I] with Serializable {
    type In = I
    type Out = O
  }

  object Performer {
    implicit def perform[H1, H2, T <: HList]: Performer[(H1 => H2) :: T, H1, H2] =
      new Performer[(H1 => H2) :: T, H1, H2] {
        def apply(l: (H1 => H2) :: T, i: H1) = l.head(i)
      }

    implicit def recurse[H, T <: HList, I, O](implicit p: Performer[T, I, O]): Performer[H :: T, I, O] =
      new Performer[H :: T, I, O] {
        def apply(l: H :: T, i: I) = p(l.tail, i)
      }
  }
}
