package initializing

import scala.concurrent._

import shapeless._

import akka.actor._
import scala.util.Random

import akkashaped._

object TokenProvider {
  case object GenerateToken
  case class TokenGenerated(token: String)

  type Shape = (GenerateToken.type => Future[TokenGenerated]) :: HNil
}

class TokenProvider extends Actor
    with Shaped[TokenProvider.Shape] {
  import TokenProvider._

  val rng = new Random()

  override def receive: Receive =
    ((_: GenerateToken.type) => respondWith(TokenGenerated(rng.nextString(12)))) ::
      HNil
}
