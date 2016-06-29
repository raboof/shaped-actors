package greeting

import scala.concurrent.Future

import akka.actor._

import shapeless._

import akkashaped._

/**
 * Adaptation of on of the examples in http://doc.akka.io/docs/akka/current/scala/actors.html,
 * demonstrating an actor that can receive multiple kinds of messages
 */
object GreetingActor {
  case class Greeting(from: String)
  case object Goodbye

  type Shape = (Greeting => Unit) :: (Goodbye.type => Unit) :: HNil
}
class GreetingActor extends Actor
    with Shaped[GreetingActor.Shape]
    with ActorLogging {
  import GreetingActor._

  override def receive =
    ((greeting: Greeting) =>
      log.info(s"I was greeted by ${greeting.from}.")) ::
      ((_: Goodbye.type) =>
        log.info("Someone said goodbye to me.")) :: HNil
}
