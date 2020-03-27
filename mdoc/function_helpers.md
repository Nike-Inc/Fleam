## Converting Async Functions into Flows

Often the same pattern will be repeated when you have a function that returns a future of some result. Fleam provides
an enrichment to make this easier.

Given we have a function like the one below.
```scala mdoc:silent
import scala.concurrent.Future

val fetchData: Int => Future[String] = { (number) =>
  // some real IO boundary or computation
  Future.successful("success!")
}
```

We might need to create a flow to be used in a graph and create the following code.
```scala mdoc:silent
import akka.stream.scaladsl._

def flowVersion(parallelism: Int): Flow[Int, String, _] = {
  Flow[Int]
   .mapAsync(parallelism)(fetchData)
}

val flow1: Flow[Int, String, _] = flowVersion(10)
```

With fleam you can easily lift this function into a flow using the function helpers and avoid the boiler-plate.
```scala mdoc:silent
import com.nike.fleam.implicits._

val flow2: Flow[Int, String, _] = fetchData.toFlow(10)
```

Fleam also provide an unordered version.
```scala
val unorderFlow: Flow[Int, String, _] = fetchData.toFlowUnordered(10)
```

If you're stuck with a def instead of a function you can make it curried to use the enrichment.
```scala
type Span = String

def fetchNumber(id: String, span: Span): (String, Span) => Future[Int] = {
  // Some IO or computation
  Future.successful(1)
}

val flow3: Flow[(String, Span), Int, _] = (fetchNumber _).toFlow(10)
```
