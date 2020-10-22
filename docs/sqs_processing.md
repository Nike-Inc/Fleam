## Using SqsSource and SqsDelete

One of the sources for a stream of data could be a Sqs Queue. Fleam provides a source and delete function to help you
process the messages.

Let's look at an example of setting up a pipeline to process the stream.

First we configure our source.
```scala
import com.typesafe.config.ConfigFactory

val config = ConfigFactory.parseString("""
{
  ourSqsSource {
    region = "us-west-2"
    queue {
      url = "https://url/to/sqs/"
    }
    source {
      batchSize = 10
      parallelism = 4
    }
    delete {
      parallelism = 4
      groupedWithin {
        batchSize = 10
        within = 1 second
      }
    }
  }
}""")
```

Now we can read this config using Ficus.
```scala
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import com.nike.fleam.sqs.configuration._

val sqsConfig = config.as[SqsQueueProcessingConfiguration]("ourSqsSource")
```

From here I'm going to show how to build a SQS processing daemon manually. There's also a convenience function available
to build a StreamDaemon using just the `SqsQueueProcessingConfiguration` config. Continue reading or [jump to that
section.](#SqsStreamDaemon).

Now we can start to create our Source. Our source will continually read from our queue as long as there is demand
downstream.
 We'll need our configuration and an Amazon SQS client.
```scala
import com.nike.fleam.sqs.SqsSource
import com.amazonaws.regions.Regions
import com.amazonaws.services.sqs.{AmazonSQSAsync, AmazonSQSAsyncClientBuilder}

val sqsClient: AmazonSQSAsync = AmazonSQSAsyncClientBuilder.standard().withRegion(Regions.fromName(sqsConfig.region)).build()

val source = SqsSource(sqsClient).forQueue(sqsConfig)
```

Now that we have our source we can start to create our pipeline. Let's imagine our pipeline is just going to log the
message Id for now. Our source will feed us a stream of Amazon SQS Messages.
```scala
import akka.stream.scaladsl._
import com.amazonaws.services.sqs.model.Message

val pipeline1 =
  Flow[Message].map { message =>
    println(message.getMessageId)
    message
  }
```

Great, but what about deleting these messages? We need to be able to tell SQS we're done with them. Let's create a
SqsDelete.
```scala
import com.nike.fleam.sqs.{SqsDelete, MessageId}
import com.nike.fleam.sqs.implicits._
import cats.implicits._

val sqsDelete = SqsDelete(sqsClient).forQueue(sqsConfig.queue.url).toFlow[Message, MessageId](sqsConfig.delete)
```

Now we can add our `sqsDelete` to our pipeline.
```scala
val pipeline2 =
  Flow[Message].map { message =>
    println(message.getMessageId)
    message
  }.via(sqsDelete)
```

Now you could use this to define a stream directly our pass these to a `StreamDaemon`.

```scala
  val daemon = new StreamDaemon("example daemon")
  daemon.start(
    source = source,
    pipeline = pipeline2,
    sink = Sink.ignore
  ) andThen { case Failure(ex) => logger.error("Unable to start daemon", ex) }
```

To expand on this a bit, imaging we only need to process messages that are within the last 5 minutes. Let's see how we
might do this. Let's define a function to get the time out of Message. We'll ignore the fact that this could throw for
the purposes of this doc.
```scala
import java.sql.Timestamp
import scala.jdk.CollectionConverters._

val getTime: Message => Timestamp = { message =>
  val epoch = message.getAttributes.asScala("SentTimestamp").toLong
  new Timestamp(epoch)
}
```

We don't want to just filter our message away since we need to make sure it goes through the pipeline and gets deleted.
We're going to need a way to represent a message that's too old. So let's define a case class and trait.
```scala
sealed trait Irrelevant; case class MessageTooOld(message: Message) extends Irrelevant
```

We need to check our `timestamp` and find the old ones. We'll use an `Either` to represent to two choices here. Either
we're going to have a good `Message` or we'll have an `Irrelevant` message.
```scala
import scala.concurrent.duration._
import java.time.Instant

val checkTime: (Message, Timestamp) => Either[Irrelevant, Message] = { (message, timestamp) =>
  if (timestamp.toInstant.isBefore(Instant.now.minusSeconds(5.minutes.toSeconds))) {
    Left(MessageTooOld(message))
  } else {
    Right(message)
  }
}
```

Let's pretend we're just going to log the messages that aren't too old. So, do we need to worry about this `Either`
thing in our function now? No, we can let the pipeline worry about that later. Let's not concern our function with it.
This way it can be re-used and only has to worry about it's own purpose.
```scala
def logMessage(message: Message): Message = {
  println(message.getBody)
  message
}
```

Now let's start putting the pieces together. Here's where we're going to handle the difference between the output of our
`checkTime`'s `Either[Instance, Message]` and the input to `logMessage`'s `Message`. We're going to use a function on
the `Flow` that lets us map over the `Message` part of our input if it actually is a `Message`. If it's not, nothing
happens and the output of `checkTime` just passes right through.
```scala
val pipeline3 = {
  Flow[Message]
    .map { message =>
      (message, getTime(message))
    }
    .map(checkTime.tupled)
    .eitherMap(logMessage _)
    .via(sqsDelete)
}
// error: type mismatch;
//  found   : akka.stream.scaladsl.Flow[com.amazonaws.services.sqs.model.Message,com.nike.fleam.sqs.BatchResult[com.amazonaws.services.sqs.model.Message],akka.NotUsed]
//  required: akka.stream.Graph[akka.stream.FlowShape[scala.util.Either[repl.Session.App.Irrelevant,com.amazonaws.services.sqs.model.Message],?],?]
//     .via(sqsDelete)
//          ^^^^^^^^^
```

Uh-oh. If we decipher the message we can see that at `.via(sqsDelete)` the compiler was expecting something that could
handle the output of `Either[Irrelevant, Message]` from the stage before it, but sqsDelete takes `Message`. This is
actually good: now we can't forget to handle all those too-old messages.

So let's define a function to turn the `MessageTooOld` back into something we can delete again.

```scala
val convertToMessage: Either[Irrelevant, Message] => Message = {
  case Right(message) => message
  case Left(MessageTooOld(message)) => message
}
```

Now let's put that into our pipeline.
```scala
val pipeline4 = {
  Flow[Message]
    .map { message =>
      (message, getTime(message))
    }
    .map(checkTime.tupled)
    .eitherMap(logMessage _)
    .map(convertToMessage)
    .via(sqsDelete)
}
```

That's it. Now our pipeline will only log messages less than 5 minutes old and deletes all of the messages.

<a name="SqsStreamDaemon"></a>
## SqsStreamDaemon

SqsStreamDaemon builds up the SQS client and takes care of sourcing and deleting messages. We just need our `sqsConfig:
SqsQueueProcessingConfiguration` we created earlier, a name, and a pipeline of `Flow[Message, Message, akka.NotUsed]`.
We can reuse the pipeline defined in the previous example minus the `sqsDelete` portion since the `SqsStreamDaemon` will
take care of that.

```scala
import com.nike.fleam.sqs.SqsStreamDaemon

val pipeline5: Flow[Message, Message, akka.NotUsed] = {
  Flow[Message]
    .map { message =>
      (message, getTime(message))
    }
    .map(checkTime.tupled)
    .eitherMap(logMessage _)
    .map(convertToMessage)
}

val daemon = SqsStreamDaemon(name = "example stream", sqsConfig = sqsConfig, pipeline5)
```

Now our `start` method is simplified since all of that was created for us. When we're ready to start we just call
`deamon.start()`.
