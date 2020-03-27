## Using SqsRetry to control message retry and dead-letter

If you're experienced with using Amazon SQS you know that queues have a built-in retry and dead-letter functionality.
So, you may wonder why you would want to use `SqsRetry` in your pipeline. What does it give you? As usual, a little more
control over exactly what's going on.

* It allows you to track the number of retries that have happened and decide whether they should continue. If necessary
  you can make that decision at a more granular level than queue. For instance you might fork your pipeline based on the
  type of message coming out of the queue and have two `SqsRetry`'s with different retry counts.
* It lets you programatically decide to dead-letter a message instead of simply failing the message however many times
  the queue's retry policy is configured to try.
* You can add informational attributes to your messages when encountering problems. This could be used to diagnose
  problem messages or provide additional context for your program to make new decisions on subsequent retries.


## Getting started

Let's first look at how we define our retry and dead-letter policies. We'll need to define at the very least a retry
policy and the dead-letter policy is optional. We'll need to define a partial function that matches on our types and
returns additional information we want to store in the message. We'll use strings and ints for this example, but you
should ideally match on something more type-safe.

```scala mdoc:silent
import com.nike.fleam.sqs.SqsRetry
import com.amazonaws.services.sqs.model._

val retryPolicy: PartialFunction[Either[String, Int], Map[String, MessageAttributeValue]] = {
  case Left("We tried, we failed.") => SqsRetry.emptyAttributes
  case Left("That user wasn't found.") =>
     Map("user service" -> new MessageAttributeValue().withStringValue("We couldn't find that user on this go."))
}
```

What we're doing here is taking in an Either of String or Int and matching on a few possible Left String values.
Anything that matches here will be retried. We've decided that if we see the message "We tried, we failed." we're going
to retry but we don't have any attributes to add. Also, we've decided that the message "That user wasn't found." will
cause this message to be retried and we're going to make a note that we couldn't find this user right now. If any other
value for this passes through, for instance `Left("abort, abort!")`, it's not going to be retried.

If we want we can define a policy to immediately send some messages to the dead-letter queue. Anything matched here will
be sent to the DL queue.

```scala mdoc:silent
val deadLetterPolicy: PartialFunction[Either[String, Int], Map[String, MessageAttributeValue]] = {
  case Left("abort, abort!") =>
    Map("abort" -> new MessageAttributeValue().withStringValue("No one can save us now!"))
}
```

## Knowing when to give up
It's possible that when we're processing a message we'll take too long. An SQS message has a time-out and if we exceed
that before we delete the message from the queue SQS will assume we didn't process it successfully and makes that
message visible again. If we're not aware of this we can end-up creating a duplicate message in SqsRetry. We'll need to
let SqsRetry know when it's been too long. SqsRetry requires proof of how to get the time the message was retrieved.

Whatever type we're sending through the pipeline when it gets to SqsRetry will need to have the time inside of it and we
need to define a `RetrievedTime` type-class for that type. Basically we just have to tell SqsRetry how to extract that
time.

```scala mdoc:silent
import com.nike.fleam.sqs.RetrievedTime
import java.time.Instant

case class Item(foo: String, bar: Int, message: Message, retrieved: Instant)

implicit val itemRetrievedTimed = RetrievedTime[Item] { item =>
  item.retrieved
}
```

When we create our SqsRetry flow it will require that this be implicitly in scope. There is a provided flow method to
time-stamp your message that will produce a `RetrievedMessage` which is just the `Message` and an `Instant`.

```scala mdoc:silent
import com.amazonaws.services.sqs.model.Message
import com.nike.fleam.sqs._
import com.nike.fleam.sqs.implicits._
import akka.stream.scaladsl.Flow

val flow: Flow[Message, RetrievedMessage, akka.NotUsed] = {
  Flow[Message]
    .timestampMessage()
}
```

## Got the message?
Same thing applies to getting the message we're dealing with in SqsRetry. We need to tell SqsRetry how to extract that
message from the type coming in. This is pretty similar to `RetrievedTime` but called `ContainsMessage` and will also be
required to be implicitly in scope when we create our retry flow.

```scala mdoc:silent
import com.nike.fleam.sqs.ContainsMessage

implicit val itemContainsMessage = ContainsMessage[Item] { item =>
  item.message
}
```

Again this is pretty simple, but this could be much more complicated depending on the type you're sending to SqsRetry so
you really need to tell it how to dig around in your data.

## Please try again later
Sometimes you might want to wait before retrying. Maybe a downstream system hasn't completed a task or some data is
otherwise unavailable. Maybe if you wait a little while you'll be able to successfully finish. You can specify a delay
function that will hide your retried message in SQS for a certain amount of time. By default this is going to be zero
seconds, but you can easily specify an amount to wait or if you need to get more complicated about it.

```scala mdoc:silent
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry
import scala.util.Random

val alwaysWait30Seconds: SendMessageBatchRequestEntry => Int = _ => 30

val waitSomeSeconds: SendMessageBatchRequestEntry => Int = { entry =>
  // Make some calculation here, maybe retries times some constant
  // Or jitter
  Random.nextInt(60)
}
```

## Config
Now down to how we configure this thing. We need to create a `SqsRetryConfiguration`.

```scala mdoc:silent
import com.nike.fleam.sqs.configuration.SqsRetryConfiguration
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

val config = ConfigFactory.parseString("""
   // This is the full configuration available, some of these like sqsProcessing, maxRetries, and retryCountKey have
   // defaults.
   retry {
     queue {
       url = "sqs://..../Queue"
     }
     deadLetterQueue {
       url = "sqs://..../DLQueue"
     }
     sqsProcessing {
       // How many concurrent fetch/deletes
       parallelism = 10
       // How many message to delete in a batch and how long to wait before just sending a partial batch
       groupedWithin {
         batchSize = 10
         within = 10 seconds
       }
     }
     maxRetries = 10
     timeout = 300 seconds
     // Message attribute key that will keep track of the number of retries
     retryCountKey = "retryCount"
  }
  """)

val retryConfig = config.as[SqsRetryConfiguration]("retry")
```

## Putting it together
Now that we have most of the pieces let's put it together. First we'll create an `SqsRetry`.

```scala mdoc:silent
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder
import com.amazonaws.regions.Regions
import scala.concurrent.ExecutionContext.Implicits.global

val sqsClient = { AmazonSQSAsyncClientBuilder
  .standard
  .withRegion(Regions.fromName("us-west-2"))
  .build
}

val sqsRetry = SqsRetry(sqsClient, retryConfig, waitSomeSeconds)
```

Now we can create our retry flow for our `Item` class.

```scala mdoc:silent:nest
val retryPolicy: PartialFunction[Item, Map[String, MessageAttributeValue]] = {
  case item if item.foo == "Failed!" => Map("foo" -> new MessageAttributeValue().withStringValue("Failed to foo!"))
}

val retryFlow: Flow[Item, Either[SqsRetryError[Item], Item], akka.NotUsed] = sqsRetry.flow(retryPolicy)
```

If you see a error message similar to `error: could not find implicit value for evidence parameter of type
com.nike.fleam.sqs.RetrievedTime[Item]` it means you either forgot to define your `RetrievedTime` for your type or it's
not in scope.

You can see here that we get back an `Either[SqsRetryError, Item]`. The "Error" part here isn't entirely accurate since
a successful retry is going to be in here, but basically the left will tell us what was done and the right side is items
that didn't need to be retried or dead-lettered.

Now that we have our retry flow we can use it wherever appropriate.

```scala mdoc:silent
import akka.stream.scaladsl.Source

  {
    Source(List.empty[Item])
      // Do some calculations...
      .via(retryFlow)
      // Log our results or tranform our Lefts into something else.
  }
```

```scala mdoc:invisible
sqsClient.shutdown()
```
