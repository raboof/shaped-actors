package akkashaped

import scala.concurrent.Future
import scala.annotation.implicitNotFound
import scala.reflect.ClassTag

import shapeless._
import shapeless.HList._
import shapeless.DepFn2

import akka.actor.{ Actor, ActorRef, Props, ActorRefFactory }
import akka.pattern.{ ask, AskableActorRef }
import akka.util.Timeout

class ShapedRef[L <: HList](ref: ActorRef) {
  import ShapedRef._

  def ask[I, O](in: I)(implicit timeout: Timeout, sender: ActorRef, performer: Performer[L, I, Future[O]], tag: ClassTag[O]): Future[O] = {
    val askable: AskableActorRef = ref;
    askable.ask(in).mapTo[O]
  }

}

object ShapedRef {
  def actorOf[T <: HList](creator: => Actor with Shaped[T])(implicit factory: ActorRefFactory): ShapedRef[T] =
    new ShapedRef(factory.actorOf(Props(creator)))

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
