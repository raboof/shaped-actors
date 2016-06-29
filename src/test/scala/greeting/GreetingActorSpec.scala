package greeting

import scala.language.postfixOps
import scala.concurrent.{ Future, Await }
import scala.concurrent.duration._

import org.scalatest._

import akka.actor.{ ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestKit }
import akka.util.Timeout

import akkashaped._

import akka.testkit.EventFilter

class GreetingActorSpec extends TestKit(ActorSystem("GreetingActorSpec"))
    with WordSpecLike
    with Matchers
    with ImplicitSender {

  implicit val timeout: Timeout = 2 seconds

  "The Greeting actor" should {
    "accept Greeting and Goodbye messages" in {
      val actor = ShapedRef.actorOf(new GreetingActor)

      EventFilter.info(message = "I was greeted by Peter.", occurrences = 1) intercept {
        actor.tell(GreetingActor.Greeting("Peter"))
      }

      EventFilter.info(message = "Someone said goodbye to me.", occurrences = 1) intercept {
        actor.tell(GreetingActor.Goodbye)
      }
    }
  }
}
