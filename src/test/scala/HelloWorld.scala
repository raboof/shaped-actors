package akkashaped

import scala.concurrent._

import shapeless._
import shapeless.HList._

import akka.actor._

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
