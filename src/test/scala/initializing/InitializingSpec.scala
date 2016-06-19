package initializing

import scala.language.postfixOps
import scala.concurrent.{ Future, Await }
import scala.concurrent.duration._

import org.scalatest._

import akka.actor.{ ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestKit }
import akka.util.Timeout

import akkashaped._

class InitializingSpec extends TestKit(ActorSystem("InitializingSpec"))
    with WordSpecLike
    with Matchers
    with ImplicitSender {

  implicit val timeout: Timeout = 2 seconds

  "The Initializing actor" should {
    "respond after receiving the token" in {
      val actor = ShapedRef.actorOf(new Initializing)

      val response: Future[Initializing.Ack.type] = actor.ask(Initializing.PerformTask("One"))
      Await.result(response, 1 second) should be(Initializing.Ack)
    }
  }
}
