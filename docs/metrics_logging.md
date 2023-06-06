## Metrics Logging

When we process items through a stream we probably want to know how many items are passing through. So let's look at
using `MetricsLogger`s to capture that data. Fleam includes two types of `MetricsLogger`s: `CloudWatch` and `TextLogging`.


Before we get into configuring our loggers, let's look at how we use one. So let's start with a flow that does nothing
but log how many items are going through it. Here you can see that a logger is nothing more than a flow that takes in
some type and passes that type through.
```scala
import akka.stream.scaladsl._
case class Item()

class Pipeline(logger: Flow[Item, Item, _]) {
  val flow = {
    Flow[Item]
      .via(logger)
  }
}
```

So this shows us that we can count anywhere along the stream. Let's look at how we create a logger using `TextLogging`.
Creating a logger is pretty easy from here. We need to give it some function `String => Unit`. Here we're going to use
a `Logger`'s info function.
```scala
import com.nike.fleam.cloudwatch._
import com.nike.fleam.logging._
import org.slf4j.LoggerFactory
import scala.concurrent.ExecutionContext.Implicits.global

val textLogger = TextLogging(LoggerFactory.getLogger("Example").info)
```

If we tried to use our `textLogger` now we'd run into some trouble.
```scala
val pipeline1 = new Pipeline(textLogger.logCount[Item])
// error: could not find implicit value for parameter f: com.nike.fleam.logging.Counter[repl.Session.App.Item,com.nike.fleam.logging.LogMessage]
// val pipeline1 = new Pipeline(textLogger.logCount[Item])
//                              ^^^^^^^^^^^^^^^^^^^^^^^^^
```
We can see that the logger needs a `Counter` for `Item`. So, easy, let's create one.

First we'll start with the configuration.
```scala
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import com.nike.fleam.configuration._
import com.typesafe.config.ConfigFactory

val config1 = ConfigFactory.parseString("""
  {
    textLogger {
      batchSize = 100000
      within = 1 seconds
    }
  }
""")

val textLoggingConfig = config1.as[GroupedWithinConfiguration]("textLogger")
```


Now we can create a `Counter` for `Item`s. A counter defines how the item is counted. The most basic one is `countWithin`
which sums items at some max `batchSize` or within some time frame. This saves us the overload of a log message for each
item. The counter doesn't impede the flow of the stream.
```scala

implicit val itemTextCounter: Counter[Item, LogMessage] = new Counter[Item, LogMessage] {
  val flow = Counters.countWithin[Item](textLoggingConfig).map(count => LogMessage(s"Processed $count items"))
}
```

Now when we try to create our pipeline it finds our counter.
```scala
val pipeline2 = new Pipeline(textLogger.logCount[Item])
```

Okay, so how much harder is it do a `CloudWatch` logger? Let's start creating one.
```scala
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import software.amazon.awssdk.regions.Region

val awsClient = CloudWatchAsyncClient.builder().region(Region.of("us-west-2")).build()
val cloudWatchLogger = CloudWatch(awsClient)
```
The main difference in creating the client is we need an `AmazonCloudWatch` instead of a function `String => Unit`.

Now we're going to need a `Counter` of `Item` for `CloudWatch` too, so let's define the config.
```scala
val config2 = ConfigFactory.parseString("""
  {
    cloudwatchLogger {
      batchSize = 100000
      within = 1 seconds
    }
  }
""")
```

Defining the `Counter` is a little different. Instead of creating a `LogMessage` we need to create a `PutMetricDataRequest`.
`Cloudwatch.wrap` helps us do this easily.
```scala
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest

val cloudwatchLoggingConfig = config2.as[GroupedWithinConfiguration]("cloudwatchLogger")

implicit val itemCloudwatchCounter: Counter[Item, PutMetricDataRequest] = new Counter[Item, PutMetricDataRequest] {
  val flow = Counters.countWithin[Item](cloudwatchLoggingConfig).map(count =>
    CloudWatch.wrap(
      namespace = "Example",
      metricName = "Item",
      count = count))
}
```

So now we can use our `CloudWatch` logger in our pipeline.
```scala
val pipeline3 = new Pipeline(cloudWatchLogger.logCount[Item])
```

So great, we can swap them out. But what if we want to log both text and send data off to CloudWatch? Well, since they're
both `Flow[Item, Item, _]` we can compose them together.
```scala
val pipeline4 = new Pipeline(textLogger.logCount[Item].via(cloudWatchLogger.logCount[Item]))
```
