Fleam release notes
-------------------

## Fleam 7.1.0

* Updates dependency versions
* Changes logback and jcl-over-slf4j dependencies to be test only

## Fleam 7.0.2

* Fixes an issue where `SqsRetry` would incorrectly not find the successful delete of message in a batch delete and produce an error instead of a success. This error was introduced in 7.0.0 by the `SqsDelete` change for the entry id.

## Fleam 7.0.1
* Fixes a wrong casing for SqsQueueProcessingConfiguration.attributeNames default value.

## Fleam 7.0.0
* Updates to AWS Java SDK 2.
* Changes the materializer for SqsSource to a UniqueKillSwitch to allow the source to be stopped without items in the
  buffer.
* Changes StreamDaemon to keep all of the materializers used by the different segments of the pipeline. When `stop` is
  called any KillSwitch materializers will be shutdown.
* Functions that directly took `Flow` or `Source` now take graph shapes for better compatibility. This shouldn't affect
  existing code.
* `Stream` and `EitherStream` have been refactored internally to apply the materializer sooner. This shouldn't affect
  existing code unless you're directly using lower level functions, in which case just remove the materializer type
  parameter.
* Changes usages of `ActorMaterializer` to `Materializer` and other smaller changes as per migration notes (https://doc.akka.io/docs/akka/current/project/migration-guide-2.5.x-2.6.x.html). 
* Changes `SqsDelete` to set the delete id to a sequential number instead of the message id. Previously duplicate messages failed. There will be no failure for duplicate messages now caused either by SQS or your own code.
* Moves cloudwatch related code from `com.nike.fleam.logging` into `com.nike.fleam.cloudwatch`.
* Adds the option to process the SQS delete `BatchResult`s via a flow passed to the `SqsStreamDaemon`.
* Removes `wrapRequest` java future wrapper from public API. Internally this is replaced by `toScala` from the Scala
  java8 compatability library for Scala 2.12 and `asScala` from the scala jdk FutureConverters for Scala 2.13.
* Removes sqs `Attributes` object. This is a typed enumeration of `MessageSystemAttributeName` in AWS Java SDK 2. All
  functions are updated to use this instead of `String`.
* Renames SQS `configuration.Default.Sqs.Attributes` to `configuration.Default.Sqs.MessageAttributes` to better reflect
  this is meant to be used for user level "message attributes" and not sqs level "attributes".
* Changes class value names that contained `Response` to `Result` where it changed in AWS Java SDK 2. Fleam classes that
  ended in `Result` haven't changed.
* Changes `MissingMessageGroupId` to `MissingGroupingKey` for `SqsReduce` to make it more general.
* Adds value class for `MessageGroupId`. Usages of SqsReduce will need to switch from `String` to `MessageGroupId`.
* Change the default config value for SQS long polling from 0 to 20 seconds
* Changes SerializedByKeyBidi to take a buffer instead of forcing one.
* Updates Scala to 2.12.12 and 2.13.3
* Changes the Akka version to 2.6.10.
* Updates cats to 2.2.0
* Updated slf4j versions and overrides to eliminate inconsistency warnings.  

## Fleam 6.0.0
* Adds the ability to recover exceptions while using a Valve to avoid having elements dropped by akka streams
  See the docs for an example usage
* Makes the `applyOne` function on Valve protected, The apply methods should be used instead of applyOne directly.

## Fleam 5.0.0
* Adds support for scala 2.13 and removes 2.11
* Removes dependency on Iota. `sqs.OpError` type is change to a simple `Either`. If you're using the provided logging
  text no change should be needed. If you're creating your own you'll now need to match on the left and right to
  determine the type of error instead of using the Iota extractions.
* Updates Akka to 2.5.26, AWS to 1.11.707, Cats to 2.1.0, Fawcett to 0.3.0
* Changes the monad requirement for stream extensions to a weakened monad, cats' `FlatMap` instead of the unnecessary
  full `Monad`.

## Fleam 4.7.0
* Adds the ability to override the default maximum retry count for SqsRetry. When calling `SqsRetry.flow` you can pass
a partial function from the `In` type to an `Int` as `retryCountOverrides`.

## Fleam 4.6.0
* Adds `.dropLeft` and `.dropRight` to Either streams allowing you to go from `Flow[?, Either[L, R], Mat]` to `Flow[?, R, Mat]` or `Flow[?, L, Mat]` respectivly.

## Fleam 4.5.0
* Adds `.eitherLeftMap` to Either streams.

## Fleam 4.4.1
* Moves to internally using [Fawcett](https://github.com/Nike-Inc/Fawcett) for manipulating AWS SQS models.

## Fleam 4.4.0
* Adds SqsEnqueue.BatchModifications.randomizeMessageDedupeIds

## Fleam 4.3.1
* Clean eviction warnings and fail build if eviction warnings are re-introduced.

## Fleam 4.3.0
* Adds some delay functions for SqsRetry. `doubleOr`, `increment`, and `constant` which can be passed to SqsRetry.

## Fleam 4.2.0
* Adds `flatten` to `Source[Either[L, Iterable[R]]]` which breaks each R into its own either item.
* Adds `flatMapConcat` as a requirement for a Stream which makes it available generically.
* Update cats version to 1.4.0

## Fleam 4.1.0
* Update (many) version numbers and add dependencyOverrides to control evictions
* Disabled unwanted scalastyle rules

## Fleam 4.0.1

* Changed the default attributesModifier in SqsRetry to be identity instead of the randomizer

## Fleam 4.0.0

* Adds randomized MessageDeduplicationId attributes when SqsRetry reenqueues a message

## Fleam 3.1.0

* Adds either instances for type classes

## Fleam 3.0.1

* Fixes missing ops import that should have been included with `import com.nike.fleam.implicits._`

## Fleam 3.0.0

* Updates dependency versions
* Moves source and flow enhancements from `import com.nike.fleam._` and `import com.nike.fleam.ops._` to
  `import com.nike.fleam.implicits._`. Each type of enhancement is also broken out separately to make it easier to
  import only needed functionality.
* Renames `MessageHolder` to `ContainsMessage`
* Renames `RetrievedMessageHolder` to `ContainsRetrievedMessage`
* Moves SQS type class instances and class enhancements into `com.nike.fleam.sqs.implicits` object
* Creates a ContainsCount type class and integrates it into SqsRetry
* Adds a default implementation for storing counts into an SQS Message
* Moves SQS configuration into the SQS package
* Moves Keyed type class outside of SerializedByKeyBidi
* Adds a Keyed instance for getting the MessageGroupId from a Message
* Changes the application of URL to SqsEnqueue and SqsDelete to avoid overloading issue and make calls more explicit
  about what type of call is being made.
* Adds a method to SqsEnqueue to enqueue a single message
* Removes the ability to pass in a function for creating a message when enqueueing. The `ToMessage` type-class should be
  used instead.
* Adds a method to SqsDelete to delete a single message
* Adds SqsReduce to collapse several messages with the same key into a single message


## Fleam 2.0.0
* Makes `ToMessage`, `MessageHolder`, `RetrievedTime`, `RetrievedMessageHolder`, and `ToMessageAttributes` contravariant.
* Changed `SerializedByKeyBidi.Keyed` from `Keyed[T, U]` to `Keyed[-T, +U]`

## Fleam 1.0.2

* Adds the ability to safely filter items when counting for reporting. Applied to TextLogging and Cloudwatch. *DOES NOT* remove items from the stream, only from the count.

```
TextLogging.logCount(logger.info, config, filter = _.isLeft)
```

## Fleam 1.0.1

Adds forgotten `eitherMapAsync` functions

## Fleam 1.0.0

* Adds a generic joinRight and joinLeft for flows

```scala
val flow: Flow[Either[A, Either[A, B]]].joinRight // results in Either[A, B]

// Also a can take a function to convert
val f: A => C
val flow: Flow[Either[A, Either[C, B]]].joinRight(f) // results in Either[C, B]

// Left version
val flow: Flow[Either[Either[A, B], B]].joinLeft // results in Either[A, B]

// Also a can take a function to convert
val f: B => C
val flow: Flow[Either[Either[A, C], B]].joinRight(f) // results in Either[A, C]
```

* Refactors flow extensions to better support Source
  Functions like `.eitherMap` were previously not available on Source

* Moves flow extensions to `ops._` to match simulacrum standard.
  This means if upgrading you'll need to import `com.nike.fleam.ops._` instead of `com.nike.fleam._`

* Adds extension to Future functions to parallelize them into streams.

```scala
val f: A => Future[B]
val as: List[A] = List(a1, a2, a3)
val parallelism = 1

val bs: Future[Seq[B]] = f.toStream(parallelism)(as)
// Equal to
val bs: Future[Seq[B]] = Source(as).mapAsync(f).runWith(Sink.seq)

// Unordered available as well
f.toStreamUnordered(parallelism)(as)
```

* Moves SQS region configuration to the correct level. The client only exist once for a pipeline so binding it to the queue wasn't correct.

```json
pipeline {
  queue = {
    url = ...
    region = ...
  }
  deadLetterQueue {
    url = ...
    region = ...
  }
}

// becomes
pipeline {
  region = ...
  queue.url = ...
  deadLetterQueue.url = ...
}
```

* Adds `logCount` functions to the logging classes to make building counters less verbose
