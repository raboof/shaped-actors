package akkashaped

import scala.language.postfixOps
import scala.concurrent.{ Future, Await }
import scala.concurrent.duration._

import org.scalatest._

import akka.actor.{ ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestKit }
import akka.util.Timeout

class HelloWorldSpec extends TestKit(ActorSystem("MySpec"))
    with WordSpecLike
    with Matchers
    with ImplicitSender {

  implicit val timeout: Timeout = 2 seconds

  "The HelloWorld actor" should {
    "type-safely greet us" in {
      val actor = ShapedRef.actorOf(new HelloWorld)

      val response: Future[HelloWorld.Greeted] = actor.ask(HelloWorld.Greet("Peter"))
      Await.result(response, 1 second) should be(HelloWorld.Greeted("Peter"))
    }
  }
}
