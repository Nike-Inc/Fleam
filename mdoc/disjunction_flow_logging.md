### Logging with disjunctions

## Summary
Continuing with our discussion on processing flows with disjunctions we're going to look at how we might log. Why might
we want to log differently than we're probably use to? A few reasons. When we log in our functions they might not have
enough information to provide a helpful message. Or, maybe we think an information message should really be an error
message or not logged at all. Maybe our errors need to be in different languages. Maybe we want to format our output for
splunk or something else that will analyze our logs. Maybe we want to log with some tracing information. Should we make
all of our functions aware of that? Probably not. So let's look at how to separate those details from our logic.


## Building up
We're going to need some errors to work with. Here we see all of the errors the `LunchOrder` class defined.

```scala mdoc:silent
import org.json4s._

type Person = String
case class Sandwich(meat: Option[String], toppings: List[String], bread: String)
case class LunchOrder(orders: Map[Person, Sandwich])

object LunchOrder {
  sealed trait LunchOrderError
  case class JsonParsingError(body: String) extends LunchOrderError
  case class DeserializationError(typeName: String, json: JValue) extends LunchOrderError
  case class InvalidBread(person: Person, sandwich: Sandwich) extends LunchOrderError
  case class Yuck(person: Person, sandwich: Sandwich, topping: String) extends LunchOrderError
}
```

## Breaking down
Now that we have some types let's write a logger we care about. How we log these errors, or if we log them, is up to us.
We only care about something that takes strings here. But this could easily take functions that need additional
information such as tracing.

```scala mdoc:silent
class LunchOrderErrorLogger(info: String => Unit, error: String => Unit) {
  import LunchOrder._
  val log: LunchOrder.LunchOrderError => Unit = {
    case JsonParsingError(body) => error(s"Couldn't parse the body into json: $body")
    case DeserializationError(typeName, json) => error(s"Couldn't deserialize json into $typeName: $json")
    case InvalidBread(person, sandwich: Sandwich) =>
      info(s"$person asked for ${sandwich.bread} bread. Give them white bread instead.")
    case Yuck(person, _, "anchovey") => info(s"$person is fishy.")
    case Yuck(person, _, "grapes") => info(s"Grapes are too round for sandwiches, $person")
    case Yuck(person, sandwich, topping) => info(s"$person ordered $topping on their sandwich $sandwich")
  }
}
```
Everything we've written exists separate from the library. We can change the message based on the error values or
ignore something completely if we wanted. Now we just need to use it. Let's put it into a flow. We're going to make a
function here that returns a `Either[LunchOrderError, LunchOrder]` just to give the flow a bit of context.

```scala mdoc:silent
import org.apache.pekko.stream.scaladsl._
import cats.implicits._

val lunchErrorLogger = new LunchOrderErrorLogger(info = println, error = println)

val someLunchOrderOperation: String => Either[LunchOrder.LunchOrderError, LunchOrder] = { _ =>
  Left(
    LunchOrder.Yuck("Peter", Sandwich(Some("ham"), List("provolone", "marshmallows"), "wheat"), "marshmallows")
  )
}

val pipeline1 = {
  Flow[String]
    .map(someLunchOrderOperation)
    .map { lunchOrder =>
      lunchOrder.leftMap(lunchErrorLogger.log)
      lunchOrder
    }
}
```

Okay, pretty easy. We're just mapping over the left side if there's an error and then letting the original value pass
through. A bit ugly. We can clean it up a bit and be more complete with our logging. Let's make our logger aware of the
disjunction we're using. This is okay, because this is at our business logic layer and gets to know the whole picture.

```scala mdoc:silent
class LunchOrderLogger(info: String => Unit, error: String => Unit, debug: String => Unit) {
  import LunchOrder._
  val log: Either[LunchOrder.LunchOrderError, LunchOrder] => Unit = {
    case Right(lunchOrder) => debug(s"Received lunch order $lunchOrder")
    case Left(JsonParsingError(body)) => error(s"Couldn't parse the body into json: $body")
    case Left(DeserializationError(typeName, json)) => error(s"Couldn't deserialize json into $typeName: $json")
    case Left(InvalidBread(person, sandwich: Sandwich)) =>
      info(s"$person asked for ${sandwich.bread} bread. Give them white bread instead.")
    case Left(Yuck(person, _, "anchovey")) => info(s"$person is fishy.")
    case Left(Yuck(person, _, "grapes")) => info(s"Grapes are too round for sandwiches, $person")
    case Left(Yuck(person, sandwich, topping)) => info(s"$person ordered $topping on their sandwich $sandwich")
  }
}
```
Now we can simplify our pipeline a bit.
```scala mdoc:silent
val lunchOrderLogger = new LunchOrderLogger(info = println, error = println, debug = println)

val pipeline2 = {
  Flow[String]
    .map(someLunchOrderOperation)
    .map { order => lunchOrderLogger.log(order); order }
}
```

This is pretty easy. We've separated our concerns and left ourselves in complete control. Hopefully this helps to show
why wouldn't want your log messages buried deep inside a library.
