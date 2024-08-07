## Using a valve

A valve is used to slow stream processing when an external system is having trouble. Unlike a Pekko circuit breaker
that fails quickly and moves onto the next item, a valve holds the failed item and retries a number of times. If the
item fails to process after a number of attempts it moves onto the next item. This keeps our pipeline from becoming
a fast-track to failure and instead slows processing while failure conditions exist.

Using a valve is fairly simple. A valve uses a CircuitBreaker to handle failure detection and manage the delay before
trying downstream systems.

First we'll need a CircuitBreaker.
```scala
import org.apache.pekko.pattern.CircuitBreaker
import concurrent.duration._

val circuitBreaker = CircuitBreaker(
  actorSystem.scheduler,
   maxFailures = 10,
   callTimeout = 1.seconds,
   resetTimeout = 5.seconds)
```

Now we can create our Valve. `maxRetries` is the number of times the valve will attempt to make a request using a single
item. After an item fails this number of times it will return the failed `Future` and move onto the next item. By default
Valve will use an exponential back-off starting at 2 seconds as a delay between requests if you don't specify a different
function. The `Valve` companion object defines `exponentialBackoff`, `constant`, and `multipled` for convenience, but
you can also pass your own custom function.

```scala
import com.nike.fleam.Valve

val valve = Valve(
  circuitBreaker = circuitBreaker,
  maxRetries = 5,
  delay = Valve.exponentialBackoff(2.seconds))
```

Once you have your valve you can wrap your async function call.
```scala
import concurrent.Future

val fetch: Int => Future[String] = { number =>
  // Real IO call here
  Future.successful(number.toString)
}

val valvedFetch: Int => Future[String] = valve(fetch)
```

I recommend keeping the composition separate, but you can also define the call as a function body.
```scala
val fetch1 = valve { number: Int =>
  // Real IO call here
  Future.successful(number.toString)
}
```

One thing to keep in mind is that with Pekko Streams if you're dropping values that throw exceptions you're going to
lose elements if they continue to fail. If you're using values like eithers to represent your errors you'll want to
recover from those exceptions after they've triggered the circuit breaker and exceeded the max retries. You can do
this by providing a partial function from Throwable to your result type.

```scala
case class CustomException(value: String) extends Throwable

case class Error(value: String)

val recoverWith: PartialFunction[Throwable, Future[Either[Error, String]]] = {
  case CustomException(value) => Future.successful(Left(Error(value)))
}

val failingFetch: Int => Future[Either[Error, String]] = { number =>
  // Real work here...
  Future.failed(new CustomException(number.toString))
}

val valvedFetchWithRecovery : Int => Future[Either[Error, String]] = valve(recoverWith)(failingFetch)
```

