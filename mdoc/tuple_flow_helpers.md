## Using Tuple-2 Flow Helpers

Sometimes when processing data through a flow you might want to keep a pair of data together but only care about operating
over one part of the pair. Fleam provides `mapRight` and `mapLeft` to make this easier and less verbose.

For example, imagine we have a map of people to distances in kilometers.

```scala mdoc:silent
type Kilometers = Double
val peopleToKilometers: Map[String, Kilometers] = Map(
  "Ryan" -> 1.0,
  "Aniket" -> 2.3,
  "Sobhagya" -> 3.4,
  "Vid" -> 0.0,
  "Michael" -> 4.0,
  "Kender" -> 2.7
)
```

Now lets also define a function to turn kilometers into miles.

```scala mdoc:silent
type Miles = Double
val kilometersToMiles: Kilometers => Miles = { kilometers => kilometers * 0.621371 }
```

We can use fleam's mapRight function to make converting the numbers easier in a flow. This way our function doesn't
need to be aware of the tuple and the flow only needs minimal boiler-plate for just working with the right side.

```scala mdoc:invisible
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

implicit val actorSystem = ActorSystem("tut")
import actorSystem.dispatcher
implicit val materializer = ActorMaterializer()
```

```scala mdoc:silent
import akka.stream.scaladsl._
import com.nike.fleam.implicits._
import concurrent.Future

val peopleToMiles: Future[Seq[(String, Miles)]] = {
  Source(peopleToKilometers.toList)
    .mapRight(kilometersToMiles)
    .runWith(Sink.seq)
}
```

Which gives us:
```scala
Success(Map(Vid -> 0.0, Kender -> 1.6777017, Ryan -> 0.621371, Aniket -> 1.4291532999999998, Sobhagya -> 2.1126614, Michael -> 2.485484))
```
```scala mdoc:invisible
actorSystem.terminate()
```
