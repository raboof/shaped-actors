package initializing

import scala.concurrent.{ Future, ExecutionContext }

import shapeless._

import akka.actor._

import akkashaped._

/**
 * A slightly more complicated example: this actor needs to aquire a token before it can
 * do any work. When `PerformTask` messages arrive before a token was aquired, a new token
 * is requested, after which we use 'become' to switch to another state and re-send the message.
 *
 * The shape of the actor guarantees that both states accept `PerformTask` messages.
 * In the uninitialized state the responsibility to respond is delegated by retrying the message
 * after the 'ask' to the TokenProvider has completed.
 *
 * This does not protect against endless loops, however - for example when the
 * `TokenGenerated` message would not move the actor to another state.
 */
object Initializing {
  case class PerformTask(payload: String)
  case object Ack

  type Shape = (PerformTask => Future[Ack.type]) :: HNil
}
class Initializing extends Actor with Shaped[Initializing.Shape] {
  import Initializing._

  implicit val ec: ExecutionContext = context.dispatcher
  val tokenProvider = ShapedRef.actorOf(new TokenProvider)

  override def receive = uninitialized

  val becomeInintializedWhenTokenGenerated: Receive = {
    case TokenProvider.TokenGenerated(token) =>
      become(initialized(token))
  }

  val triggerGeneratingToken: Receive =
    ((command: PerformTask) => {
      shapedSelf.retryAfter(
        command,
        tokenProvider.ask(TokenProvider.GenerateToken)
      )
    }) :: HNil

  val uninitialized: Receive =
    becomeInintializedWhenTokenGenerated orElse triggerGeneratingToken

  def initialized(token: String) =
    ((command: PerformTask) => {
      respondWith(Ack)
    }) :: HNil
}
