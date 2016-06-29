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

  def ask[I, O](in: I)(implicit timeout: Timeout, sender: ActorRef, witness: ShapeWitness[L, I, O]): O = {
    val askable: AskableActorRef = ref;
    askable.ask(in).asInstanceOf[O]
  }

  def tell[I](in: I)(implicit sender: ActorRef, witness: ShapeWitness[L, I, Unit]): Unit = ref ! in

  def retryAfter[I, O](in: I, future: Future[_])(implicit timeout: Timeout, sender: ActorRef, witness: ShapeWitness[L, I, O], ec: ExecutionContext): O = {
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

  @implicitNotFound("No function of type ''${I} => ${O}'' found in shape ${L}.")
  trait ShapeWitness[L <: HList, I, O] extends DepFn2[L, I] with Serializable {
    type In = I
    type Out = O
  }

  object ShapeWitness {
    implicit def witness[H1, H2, T <: HList]: ShapeWitness[(H1 => H2) :: T, H1, H2] =
      new ShapeWitness[(H1 => H2) :: T, H1, H2] {
        def apply(l: (H1 => H2) :: T, i: H1) = l.head(i)
      }

    implicit def recurse[H, T <: HList, I, O](implicit p: ShapeWitness[T, I, O]): ShapeWitness[H :: T, I, O] =
      new ShapeWitness[H :: T, I, O] {
        def apply(l: H :: T, i: I) = p(l.tail, i)
      }
  }
}
