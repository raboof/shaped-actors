# shaped-actors

Experiment for more precisely typed actors based on shapeless.

## background

One of the strengths of the actor model as implemented in Akka is the
simplicity of the model: actors can send each other messages, and Akka
guarantees they will be passed to the actors' implementation sequentially.
During message processing the actor can decide to change the behavior,
and subsequent messages will be handled according to that new behavior.
Finally when an actor crashes its supervisor can make sure this is dealt with,
and for example restart the actor and have it resume processing messages,
transparently to the sender.

### ask

There are many ways for actors to communicate with each other. The 'ask'
pattern is a simple 'request-response' model: you send the actor a message,
and receive a Future that will complete as soon as the actor responds to this
message.

### adding types

Unfortunately, the 'ask' signature looks like this (simplified):

```
def ask(message: Any): Future[Any]
```

The 'contract' between the actor and the outside world is entirely implicit.

## actor shapes

This repo is an experiment to see if we can have the compiler help us more
by introducing a way to express the contract the actor exposes to the world.

I'm using simple function types: to express an actor should respond to a
`Greet` message with a `Greeted` response, I specify `Greet => Future[Greeted]`.

When an actor accepts a `Goodbye` case object but will not send any response,
`Goodbye.type => Unit`.

To allow actors to respond to different kinds of commands with different kinds of
responses, I compose these functions together in an HList:

```
type Shape = (Greet => Future[Greeted]) :: (Goodbye.type => Unit) :: HNil
```

Then I can define an actor that has this shape by mixing in the `Shape` trait:

```
class HelloWorld extends Actor with Shaped[HelloWorld.Shape] {
  ...
}
```

This introduces some useful helper functions to make sure the implementation
indeed behaves according to the shape.

To get an `ActorRef` that is aware of the shape of the actor and thus can expose
more conveniently typed `tell` and `ask` patterns, we introduce the `ShapedRef`:

```
val actor = ShapedRef.actorOf(new HelloWorld)

val response: Future[HelloWorld.Greeted] = actor.ask(HelloWorld.Greet("Peter"))
Await.result(response, 1 second) should be(HelloWorld.Greeted("Peter"))
```

## examples

### Greeting

Let's start by adapting the first example in the official Akka documentation: the
GreetingActor. It simply writes some log messages whenever it receives a `Greeting`
or `Goodbye` message. As it does not send any responses, the shape looks like this:

```
type Shape = (Greeting => Unit) :: (Goodbye.type => Unit) :: HNil
```

The actor, save its actual behavior, now looks like:

```
object GreetingActor {
  case class Greeting(from: String)
  case object Goodbye

  type Shape = (Greeting => Unit) :: (Goodbye.type => Unit) :: HNil
}
class GreetingActor extends Actor
    with Shaped[GreetingActor.Shape]
    with ActorLogging {
  import GreetingActor._

  override def receive = ???
}
```

Because of the shape, as a consumer we now have access to a `tell` function
that will accept any messages that are part of the shape, but not others:

```
val actor = ShapedRef.actorOf(new GreetingActor)
actor.tell(GreetingActor.Greeting("Peter"))
actor.tell(GreetingActor.Goodbye)

actor.tell("Something else") // compiler error
```

The implementation can consist of a couple of Scala functions that are of
the types in the shape. We'll get a compiler error if we forget any or write
functions of unexpected types:

```
override def receive =
  ((greeting: Greeting) =>
    log.info(s"I was greeted by ${greeting.from}.")) ::
  ((_: Goodbye.type) =>
    log.info("Someone said goodbye to me.")) :: HNil
```

* View the actor code [here](src/test/scala/greeting/GreetingActor.scala)
* View the consumer code [here](src/test/scala/greeting/GreetingActorSpec.scala)

### Hello, World

To make things a little more interesting, let's move on to an actor that
actually responds to messages, using the 'ask' pattern.

As an example I've taken the 'Hello World' example of the official (but experimental)
Akka 'Typed Actors'.

* View the actor code [here](src/test/scala/helloworld/HelloWorld.scala)
* View the consumer code [here](src/test/scala/helloworld/HelloWorldSpec.scala)

To contrast with 'Typed Actors', shapes have a couple of nice properties:

* The messages are not 'polluted' with 'sender' actor references
* While a Typed Actor cannot reply with a message of the wrong type, it can forget to reply altogether. The Shape implementation will not compile if it doesn't return a `Future`.

### Chat room

The Typed Actors documentation has a more complex example in the form of
a Chat Room actor. The types encode an actor that accepts only `GetSession`
messages. When sending it a `GetSession` the reply contains a new reference
to the same actor, but this time typed to express it now accepts `PostMessage`
messages.

While it does have some nice properties, I find this actor design unconvincing:
IMHO it is a pretty fundamental feature of Akka that an actor can crash and be
restarted invisibly to anyone holding an `ActorRef` to that actor.
This example sort of breaks that, because it exposes an
`ActorRef[PostMessage]`, but after restarting the actor might not in fact
accept `PostMessage` messages from 'old' clients at all.

TODO: give example of an alternative Shaped protocol.

### Initializing with a Token

A slightly more complicated example: this actor needs to acquire a token before it can
do any work. When `PerformTask` messages arrive before a token was acquired, a new token
is requested, after which we use 'become' to switch to another state and re-send the message.

The shape of the actor guarantees that both states accept `PerformTask` messages.
In the uninitialized state the responsibility to respond is delegated by retrying the message
after the 'ask' to the `TokenProvider` has completed.

This is not entirely fool-proof, of course, but seems to reflect the intent behind the code more
clearly than without types.

* View the actor code [here](src/test/scala/initializing/Initializing.scala) (and the token provider [here](src/test/scala/initializing/TokenProvider.scala))
* View the consumer code [here](src/test/scala/initializing/InitializingSpec.scala)

## limitations

* Looks ugly
* Domain needs to be disjunct classes: no inheritance or more interesting matching
* Depends on Shapeless

## in short

TODO summarize, emphasize it's mostly an interesting experiment, not really something
I'd use in production.
