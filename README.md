# Fleam

![Fleam logo][logo]

Fleam is a library designed to help create disjunctive (Eithers) and more generally monadic streams using
[Cats](https://github.com/typelevel/cats/tree/master/core/src/main/scala/cats) and [Akka
Streams](https://doc.akka.io/docs/akka/2.5/stream/).

In the disjunctive case this means that as data flows through the stream if it becomes a `Left` it will no longer be
processed by the disjunctive stages and will pass through continuing further down stream.

## Either Stream enhancements
* `eitherMap` - Applies a function to items that are Right where only the right-hand value is passed and the result is
  placed back into the Either `R => R1`
* `eitherFlatMap` - Applies a function to items that are Right where only the right-hand value is passed and the result
  is a new either. `R => Either[L, R1]`
* `eitherMapAsync`, `eitherMapAsyncUnordered`, `eitherFlatMapAsync`, `eitherFlatMapAsyncUnordered`,
  `eitherFlatMapAsyncUnordered` - Future based versions of `eitherMap` and `eitherFlatMap` that preserve item order or
  not.
* `flatten` - On an Either turns a Right Iterable into individual Rights
* `flatMapConcat` - Takes a function from `Either[L, R]` that creates a Source of eithers to be introduced into the
  stream.
* `broadcastMerge` - process an item through a collection of flows in parallel and merge the results back into the
  stream
* `joinRight` - Join nested eithers to the right a stream of `Either[L, [Either[L, R]]` becomes `Either[L, R]`
* `joinLeft` - Join nested eithers to the left a stream of `Either[Either[L, R], R]` becomes `Either[L, R]`
* `viaRight` - `eitherMap`, but takes a flow instead of a function
* `viaLeft` - process left values through a flow
* `biVia` - Process Lefts and Rights through different flow and merge the results back into the stream

## General Stream enhancements
* `StreamDaemon` a class that manages starting and cleanly stopping a stream.
* Type classes for stream based metrics logging whereby data is created as part of the stream.
* `tickingGroupedWithin` - Like a normal `groupedWithin` except will emit an empty Seq after the elapsed `within`
  duration even if nothing has been received.
* `SerializedByKeyBidi` - a
  [BidiFlow](https://doc.akka.io/docs/akka/2.5.4/scala/stream/stream-graphs.html#bidirectional-flows) that limits items
  by a key to serial processing. For example an items of key `A` will while have to complete processing before another
  item of key `A` will be processed. This helps prevent concurrent operations for a key.
* `Valve` - Slows processing during failed downstream services instead of failing fast.
* `ContainsCount` - typeclass to require the ability to extract a count for an item. Used to track repeated trips
  through a processing stream.
* Case class based configuration for common parameters, `GroupedWithinConfiguration`, `ThrottleConfiguration`,
  `CircuitBreakerConfiguration`
* Enrichments to convert a function that returns a future into a flow
* Enrichments to help with processing tuples through flows

# Fleam SQS

Fleam SQS is a library of classes to aid in processing AWS SQS messages in a functional manner. In practice this means
providing operations that are complete and explicitly handling retry and dead-letter scenarios instead of relying on
message timeouts.

* `ContainsRetrievedMessage` - typeclass to require an SQS message is extractable in an item
* `ToMessage` - typeclass to turn an object into a new SQS Message
* `MessageAttributes` - provides an instance of `ContainsCount` that stores a count in an SQS message's
  messageAttributes
* `RetrievedTime` - typeclass which requires a retrieved time for the message and calculates the elapsed time.
* `SqsSource` - throttlable `Source` for reading messages from an SQS Queue.
* `SqsDelete` - flow to delete SQS message individually or in batches.
* `SqsEnqueue` - flow to enqueue SQS messages individually or in batches. 
* `SqsReduce` - combines messages within a grouping window with the same key into a single message and deletes the
  duplicate messages.
* `SqsRetry` - explicit handling of retry and dead-letters. Takes two partial functions to define each group. Provides
  options for back-off, retry count, timeout, error inclusion in the message, and message duplication id modification.
* Case class based Configuration
* `toMessageAttributes` - extension on tuples to create maps of MessageAttributeValue
* Cats' `Show` instances for logging SqsRetry Errors

# Fleam Cloudwatch

Provides a class to create a flow which logs a count to Cloudwatch as part of the stream. Often used to create a metric
of items processed.

## Getting Started

Enhancements to streams can be imported using `import com.nike.fleam.implicits._`

SQS specific enhancements can be imported using `import com.nike.fleam.sqs.implicits._`

## Docs
  * [Turning Async Function into Flows](docs/function_helpers.md)
  * [Using Tuple-2 Flow Helpers](docs/tuple_flow_helpers.md)
  * [Using Valves to slow processing when external system are unavailable](docs/valves.md)
  * [Processing SQS pipelines](docs/sqs_processing.md)
  * [Logging stream metrics](docs/metrics_logging.md)
  * [Logging with disjunction flows](docs/disjunction_flow_logging.md)
  * [Using SqsRetry to manage your retry and dead-letter policies](docs/sqsretry.md)

## Contributing

See our [Contributing](CONTRIBUTING.md) guidelines.

## Developing

```sh
sbt ~test
```

## Running the Checks
```sh
sbt check
```

[logo]: images/fleam@250px.png "Fleam logo"
