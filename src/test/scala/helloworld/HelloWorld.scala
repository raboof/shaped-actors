package helloworld

import scala.concurrent._

import shapeless._
import shapeless.HList._

import akka.actor._

import akkashaped._

/**
 * Minimal example of 'shaped actors'.
 *
 * The Shape (protocol) simply says a 'greet' will be responded to with a 'Greeted'.
 *
 * Because of the 'shaped actors' infrastructure, when we fail to return a `Future[Greeted]` in the
 * `receive` we will get a compiler error. Also in the asking side (see HelloWorldSpec.scala), we'll
 * get a `Future[Greeted]` rather than a `Future[Any]` from the call to `ShapedRef.ask`.
 */
object HelloWorld {
  type Shape = (HelloWorld.Greet => Future[HelloWorld.Greeted]) :: HNil

  case class Greet(whom: String)
  case class Greeted(whom: String)
}

class HelloWorld extends Actor
    with Shaped[HelloWorld.Shape] {
  import HelloWorld._
  import Shaped._

  override def receive: Receive =
    ((c: Greet) => respondWith(Greeted(c.whom))) ::
      HNil

}
