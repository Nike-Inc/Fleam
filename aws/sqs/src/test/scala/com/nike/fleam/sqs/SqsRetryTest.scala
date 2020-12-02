package com.nike.fleam
package sqs

import java.time.Instant

import akka.stream.scaladsl._
import software.amazon.awssdk.services.sqs.model._
import configuration._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues
import cats.implicits._
import implicits._
import com.nike.fawcett.sqs._
import monocle.syntax.all._
import scala.jdk.CollectionConverters._
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.Try

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class SqsRetryTest extends AnyFlatSpec with Matchers with ScalaFutures with EitherValues {

  behavior of "SqsRetry"

  import TestTools._

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 10.millis)

  sealed trait Examples {
    def retrieved: RetrievedMessage
  }
  case class UhOh(retrieved: RetrievedMessage) extends Examples
  case class KaBoom(retrieved: RetrievedMessage) extends Examples
  case class NumberedKerplow(i: Int, retrieved: RetrievedMessage) extends Examples

  implicit val examplesContainsMessage = new ContainsMessage[Examples] {
    def getMessage(example: Examples): Message = example.retrieved.message
  }

  implicit val examplesRetrievedTime = new RetrievedTime[Examples] {
    def getRetrievedTime(example: Examples) =
      implicitly[RetrievedTime[RetrievedMessage]].getRetrievedTime(example.retrieved)
  }

  val currentTime = Instant.ofEpochMilli(1495226445751L)

  def newMessage() = Message.builder().build()

  def mockSqsRetry(
      enqueueMessages: List[Message] => Future[Either[SqsEnqueueError, SendMessageBatchResponse]] =
        messages => Future.successful(Right(SendMessageBatchResponse.builder().build())),
      deleteMessages: List[Message] => Future[BatchResult[Message]] =
        messages => Future.successful(BatchResult(
          deleteMessageBatchResponse = DeleteMessageBatchResponse.builder()
            .successful(messages.map(m => DeleteMessageBatchResultEntry.builder().id(m.messageId).build()):_*)
            .build(),
          failed = Nil,
          successful = messages
            .map(m => SuccessfulResult(m, DeleteMessageBatchResultEntry.builder().id(m.messageId).build()))
          )),
      deadLetterEnqueueMessage: List[Message] => Future[Either[SqsEnqueueError, SendMessageBatchResponse]] =
        messages => Future.successful(Right(SendMessageBatchResponse.builder.build())),
      maxRetries: Int = 10,
      now: () => Instant = () => currentTime
    ) = new SqsRetry(
      reEnqueueMessages = (messages, _) => enqueueMessages(messages),
      deleteMessages = deleteMessages,
      deadLetterEnqueueMessages = (messages, _) => deadLetterEnqueueMessage(messages),
      sqsProcessing = Default.Sqs.enqueueConfig,
      maxRetries = maxRetries,
      timeout = 20.seconds,
      now = now,
      retryCountKey = "retryCount"
    )

  it should "enqueue messages that are able to be retried without randomized MessageDeduplicationId when attributesModifier is not specified" in {
    val messagesReceived = Promise[List[Message]]()
    val sqsRetry = mockSqsRetry(
      enqueueMessages = messages => {
        messagesReceived.success(messages)
        Future.successful(Right(SendMessageBatchResponse.builder().build()))
      }
    )

    val previousDedupId = "foo"

    val retrieved = RetrievedMessage(
      newMessage().toBuilder
        .attributes(Map(MessageSystemAttributeName.MESSAGE_DEDUPLICATION_ID -> previousDedupId).asJava)
        .build(),
      Instant.now)

    val flow = sqsRetry.flow[Either[Examples, Examples]](
      retry = { (in: Either[Examples, Examples]) =>
        in match {
          case Left(UhOh(retrieved)) => ("uh-oh's" -> "We uh-oh'd").toMessageAttributes
        }
      }
    )

    val pipeline = Source.single(Left(UhOh(retrieved)))
      .via(flow)
      .runWith(Sink.head)

    whenReady(checkSideEffect(pipeline, messagesReceived)) { messages =>
      messages.length should be(1)
      val message = messages.head
      message.messageAttributes.asScala shouldBe Map(
        "uh-oh's" -> MessageAttributeValue.builder().dataType("String").stringValue("We uh-oh'd").build(),
        "retryCount" -> MessageAttributeValue.builder().dataType("Number").stringValue("1").build()
      )
      message.attributes.asScala(MessageSystemAttributeName.MESSAGE_DEDUPLICATION_ID) shouldBe previousDedupId
    }
  }

  it should "enqueue messages that are able to be retried with randomized MessageDeduplicationId when attributesModifier is specified" in {
    val messagesReceived = Promise[List[Message]]()
    val sqsRetry = mockSqsRetry(
      enqueueMessages = messages => {
        messagesReceived.success(messages)
        Future.successful(Right(SendMessageBatchResponse.builder.build()))
      }
    )

    val previousDedupId = "foo"

    val retrieved = RetrievedMessage(
      newMessage().toBuilder()
        .attributes(Map(MessageSystemAttributeName.MESSAGE_DEDUPLICATION_ID -> previousDedupId).asJava)
        .build(),
      Instant.now)

    val flow = sqsRetry.flow[Either[Examples, Examples]](
      retry = { (in: Either[Examples, Examples]) =>
        in match {
          case Left(UhOh(retrieved)) => ("uh-oh's" -> "We uh-oh'd").toMessageAttributes
        }
      },
      attributesModifier = SqsRetry.randomizeMessageDedupeId()
    )

    val pipeline = Source.single(Left(UhOh(retrieved)))
      .via(flow)
      .runWith(Sink.head)

    whenReady(checkSideEffect(pipeline, messagesReceived)) { messages =>
      messages.length should be(1)
      val message = messages.head
      message.messageAttributes.asScala shouldBe Map(
        "uh-oh's" -> MessageAttributeValue.builder().dataType("String").stringValue("We uh-oh'd").build(),
        "retryCount" -> MessageAttributeValue.builder().dataType("Number").stringValue("1").build()
      )
      message.attributes.asScala(MessageSystemAttributeName.MESSAGE_DEDUPLICATION_ID) should not equal previousDedupId
    }
  }

  it should "increment the retry count" in {
    val messagesReceived = Promise[List[Message]]()
    val sqsRetry = mockSqsRetry(
      enqueueMessages = messages => {
        messagesReceived.success(messages)
        Future.successful(Right(SendMessageBatchResponse.builder().build()))
      }
    )

    val retrieved = RetrievedMessage(
      message = newMessage()
        .applyLens(MessageLens.messageAttributes)
        .modify(_ + ("retryCount" -> MessageAttributeValue.builder().stringValue("1").dataType("Number").build())),
      timestamp = Instant.now
    )


    val flow = sqsRetry.flow[Either[Examples, Examples]](
      retry = { (in: Either[Examples, Examples]) =>
        in match {
          case Left(UhOh(retrieved)) => SqsRetry.emptyAttributes
        }
      }
    )

    val pipeline = Source.single(Left(UhOh(retrieved)))
      .via(flow)
      .runWith(Sink.head)

    whenReady(checkSideEffect(pipeline, messagesReceived)) { messages =>
      messages.length should be(1)
      val message = messages.head
      message.messageAttributes.asScala shouldBe Map(
        "retryCount" -> MessageAttributeValue.builder().stringValue("2").dataType("Number").build()
      )
    }
  }

  it should "let you override the max retry count" in {
    val messagesReceived = Promise[List[Message]]()

    val maxRetries = 10

    val sqsRetry = mockSqsRetry(
      enqueueMessages = messages => {
        messagesReceived.success(messages)
        Future.successful(Right(SendMessageBatchResponse.builder.build()))
      },
      maxRetries = maxRetries
    )

    val retrieved = RetrievedMessage(
      message = newMessage().applyLens(MessageLens.messageAttributes)
        .modify(_ + ("retryCount" -> MessageAttributeValue.builder().stringValue("11").dataType("Number").build())),
      timestamp = Instant.now
    )


    val flow = sqsRetry.flow[Either[Examples, Examples]](
      retry = { (in: Either[Examples, Examples]) =>
        in match {
          case Left(UhOh(retrieved)) => SqsRetry.emptyAttributes
        }
      },
      retryCountOverrides = { case Left(UhOh(retrieved)) => maxRetries + 10 }
    )

    val pipeline = Source.single(Left(UhOh(retrieved)))
      .via(flow)
      .runWith(Sink.head)

    whenReady(checkSideEffect(pipeline, messagesReceived)) { messages =>
      messages.length should be(1)
      val message = messages.head
      message.messageAttributes.asScala shouldBe Map(
        "retryCount" -> MessageAttributeValue.builder().stringValue("12").dataType("Number").build()
      )
    }
  }

  it should "let you override the retry count to less than the default max" in {
    val messagesReceived = Promise[List[Message]]()
    val sqsRetry = mockSqsRetry(
      deadLetterEnqueueMessage = messages => {
        messagesReceived.success(messages)
        Future.successful(Right(SendMessageBatchResponse.builder()
          .successful(SendMessageBatchResultEntry.builder().id("1").build())
          .build()))
      },
      maxRetries = 10
    )

    val retrieved = RetrievedMessage(
      message = newMessage().applyLens(MessageLens.messageAttributes)
        .modify(_ + ("retryCount" -> MessageAttributeValue.builder().stringValue("4").dataType("Number").build()))
        .toBuilder
        .messageId("1")
        .build(),
      timestamp = Instant.now
    )

    val flow = sqsRetry.flow[Either[Examples, Examples]](
      retry = { (in: Either[Examples, Examples]) =>
        in match {
          case Left(UhOh(retrieved)) => SqsRetry.emptyAttributes
        }
      },
      retryCountOverrides = { case Left(UhOh(_)) => 3 }
    )

    val pipeline = Source.single(Left(UhOh(retrieved)))
      .via(flow)
      .runWith(Sink.head)

    whenReady(messagesReceived.future) { messages =>
      messages.length should be(1)
      val message = messages.head
      message.messageAttributes.asScala shouldBe Map(
        "retryCount" -> MessageAttributeValue.builder().stringValue("4").dataType("Number").build()
      )
      message.messageId shouldBe "1"
    }

    whenReady(pipeline) { _ should be(Left(
      ExceededRetriesDeadLettered(
        message = retrieved.message.applyLens(MessageLens.attributes).set(Map.empty),
        in =  Left(UhOh(retrieved)),
        maxRetries = 3)))
    }
  }
  it should "deadletter messages that exceed the max retries" in {
    val messagesReceived = Promise[List[Message]]()
    val sqsRetry = mockSqsRetry(
      deadLetterEnqueueMessage = messages => {
        messagesReceived.success(messages)
        Future.successful(Right(SendMessageBatchResponse.builder()
          .successful(SendMessageBatchResultEntry.builder()
            .id("1")
            .build()
          ).build()))
      }
    )

    val retrieved = RetrievedMessage(
      message = newMessage()
        .applyLens(MessageLens.messageAttributes)
        .modify(_ + ("retryCount" -> MessageAttributeValue.builder().stringValue("11").dataType("Number").build()))
        .toBuilder()
        .messageId("1")
        .build(),
      timestamp = Instant.now
    )

    val flow = sqsRetry.flow[Either[Examples, Examples]](
      retry = { (in: Either[Examples, Examples]) =>
        in match {
          case Left(UhOh(retrieved)) => SqsRetry.emptyAttributes
        }
      }
    )

    val pipeline = Source.single(Left(UhOh(retrieved)))
      .via(flow)
      .runWith(Sink.head)

    whenReady(messagesReceived.future) { messages =>
      messages.length should be(1)
      val message = messages.head
      message.messageAttributes.asScala shouldBe Map(
        "retryCount" -> MessageAttributeValue.builder().stringValue("11").dataType("Number").build()
      )
      message.messageId shouldBe "1"
    }

    whenReady(pipeline) { _ should be(Left(
      ExceededRetriesDeadLettered(
        message = retrieved.message.applyLens(MessageLens.attributes).set(Map.empty),
        in =  Left(UhOh(retrieved)),
        maxRetries = 10)))
    }
  }

  it should "deadletter messages indicate as such" in {
    val messagesReceived = Promise[List[Message]]()
    val sqsRetry = mockSqsRetry(
      deadLetterEnqueueMessage = messages => {
        messagesReceived.success(messages)
        Future.successful(Right(SendMessageBatchResponse.builder.build()))
      }
    )

    val retrieved = RetrievedMessage(newMessage(), Instant.now)

    val flow = sqsRetry.flow[Either[Examples, Examples]](
      deadLetter = { (in: Either[Examples, Examples]) =>
        in match {
          case Left(KaBoom(retrieved)) =>
            Map("fatal-error" ->
              MessageAttributeValue.builder().dataType("String").stringValue("Got KaBoomed").build())
        }
      },
      retry = PartialFunction.empty
    )

    val pipeline = Source.single(Left(KaBoom(retrieved)))
      .via(flow)
      .runWith(Sink.head)

    whenReady(checkSideEffect(pipeline, messagesReceived)) { messages =>
      messages.length should be(1)
      val message = messages.head
      message.messageAttributes.asScala shouldBe Map(
        "fatal-error" -> MessageAttributeValue.builder().dataType("String").stringValue("Got KaBoomed").build()
      )
    }
  }

  it should "enqueue dead letters before deleting them" in {
    sealed trait Status
    case object Enqueued extends Status
    case object Deleted extends Status

    val first = Promise[Status]()

    val sqsRetry = mockSqsRetry(
      deadLetterEnqueueMessage = messages => {
        first.tryComplete(Try(Enqueued))
        Future.successful(Right(SendMessageBatchResponse.builder.build()))
      },
      deleteMessages = messages => {
        first.tryComplete(Try(Deleted))
        Future.successful(BatchResult(DeleteMessageBatchResponse.builder.build(), Nil, Nil))
      }
    )

    val retrieved = RetrievedMessage(newMessage(), Instant.now)

    val flow = sqsRetry.flow[Either[Examples, Examples]](
      deadLetter = { (in: Either[Examples, Examples]) =>
        in match {
          case Left(KaBoom(retrieved)) => ("fatal-error" -> "Got KaBoomed").toMessageAttributes
        }
      },
      retry = PartialFunction.empty
    )

    val pipeline = Source.single(Left(KaBoom(retrieved)))
      .via(flow)
      .runWith(Sink.head)

    checkSideEffect(pipeline, first).futureValue should be(Enqueued)
  }

  it should "enqueue before deleting retries" in {

    sealed trait Status
    case object Enqueued extends Status
    case object Deleted extends Status

    val first = Promise[Status]()

    val sqsRetry = mockSqsRetry(
      enqueueMessages = messages => {
        first.tryComplete(Try(Enqueued))
        Future.successful(Right(SendMessageBatchResponse.builder.build()))
      },
      deleteMessages = messages => {
        first.tryComplete(Try(Deleted))
        Future.successful(BatchResult(DeleteMessageBatchResponse.builder.build(), Nil, Nil))
      }
    )

    val retrieved = RetrievedMessage(newMessage(), Instant.now)

    val flow = sqsRetry.flow[Either[Examples, Examples]](
      deadLetter = { (in: Either[Examples, Examples]) =>
        in match {
          case Left(KaBoom(retrieved)) => ("fatal-error" -> "Got KaBoomed").toMessageAttributes
        }
      },
      retry = PartialFunction.empty
    )

    val pipeline = Source.single(Left(KaBoom(retrieved)))
      .via(flow)
      .runWith(Sink.head)

    checkSideEffect(pipeline, first).futureValue should be(Enqueued)
  }

  it should "not delete messages that failed to enqueue" in {

    val failedResult = BatchResultErrorEntry.builder()
      .id("1")
      .code("1")
      .message("You failed!")
      .build()

    val sqsRetry = mockSqsRetry(
      enqueueMessages = messages => {
        Future.successful(Right(SendMessageBatchResponse.builder().failed(List(failedResult).asJava).build()))
      },
      deleteMessages = messages => {
        if (messages.isEmpty)
          Future.successful(BatchResult(DeleteMessageBatchResponse.builder().build(), Nil, Nil))
        else
          Future.failed(new RuntimeException("no messages should be deleted"))
      }
    )

    val message = Message.builder().messageId("1").build()
    val retrieved = RetrievedMessage(message, Instant.now)

    val flow = sqsRetry.flow[Either[Examples, Examples]](
      retry = { (in: Either[Examples, Examples]) =>
        in match {
          case Left(UhOh(retrieved)) =>
            Map("uh-oh's" -> "We uh-oh'd").toMessageAttributes
        }
      }
    )

    val pipeline = Source.single(Left(UhOh(retrieved)))
      .via(flow)
      .runWith(Sink.head)

    pipeline.futureValue should be {
      import MessageLens._
      val updatedMessage =
        retrieved.message
          .applyLens(messageAttributes).modify(
            _ ++
            Map("uh-oh's" -> "We uh-oh'd").toMessageAttributes ++
            Map("retryCount" -> 1).toMessageAttributes
          )
          .applyLens(attributes).set(Map.empty)

      RetryEnqueueError(
        updatedMessage,
        Left(UhOh(retrieved)),
        OpFailure(updatedMessage, Left(EntryError("1", "You failed!")))).asLeft
    }
  }

  it should "not delete messages that failed to dlq" in {

    val failedResult = BatchResultErrorEntry.builder()
      .id("1")
      .code("1")
      .message("You failed!")
      .build()

    val sqsRetry = mockSqsRetry(
      deadLetterEnqueueMessage = messages => {
        Future.successful(Right(SendMessageBatchResponse.builder().failed(List(failedResult).asJava).build()))
      },
      deleteMessages = messages => {
        if (messages.isEmpty)
          Future.successful(BatchResult(DeleteMessageBatchResponse.builder.build(), Nil, Nil))
        else
          Future.failed(new RuntimeException("no messages should be deleted"))
      }
    )

    val message = Message.builder().messageId("1").build()
    val retrieved = RetrievedMessage(message, Instant.now)

    val flow = sqsRetry.flow[Either[Examples, Examples]](
      deadLetter = { (in: Either[Examples, Examples]) =>
        in match {
          case Left(UhOh(retrieved)) => ("uh-oh's" -> "We uh-oh'd").toMessageAttributes
        }
      },
      retry = PartialFunction.empty
    )

    val pipeline = Source.single(Left(UhOh(retrieved)))
      .via(flow)
      .runWith(Sink.head)

    pipeline.futureValue should be {
      import MessageLens._
      val updatedMessage = retrieved.message
        .applyLens(messageAttributes).modify(_ ++ Map("uh-oh's" -> "We uh-oh'd").toMessageAttributes)

      RetryDlqError(
        updatedMessage,
        Left(UhOh(retrieved)),
        OpFailure(updatedMessage, Left(EntryError("1", "You failed!")))).asLeft
    }
  }

  it should "report re-enqueued deletion failures" in {

    val failedResult = BatchResultErrorEntry.builder()
      .id("1")
      .code("1")
      .message("You failed!")
      .build()

    val message = Message.builder().messageId("foo").build()

    val sqsRetry = mockSqsRetry(
      enqueueMessages = messages => {
        Future.successful(Right(SendMessageBatchResponse.builder()
          .successful(messages.map(m => SendMessageBatchResultEntry.builder().id(m.messageId).build()):_*).build()
        ))
      },
      deleteMessages = messages => {
          Future.successful(BatchResult(
            DeleteMessageBatchResponse.builder.build(),
            List(FailedResult(message, failedResult)), Nil))
      }
    )

    val retrieved = RetrievedMessage(message, Instant.now)

    val flow = sqsRetry.flow[Either[Examples, Examples]](
      retry = { (in: Either[Examples, Examples]) =>
        in match {
          case Left(UhOh(retrieved)) => ("uh-oh's" -> "We uh-oh'd").toMessageAttributes
        }
      }
    )

    val pipeline = Source.single(Left(UhOh(retrieved)))
      .via(flow)
      .runWith(Sink.head)


    pipeline.futureValue should be {
      import MessageLens._
      val updatedMessage =
        retrieved.message
          .applyLens(messageAttributes).modify(
            _ ++
            Map("uh-oh's" -> "We uh-oh'd").toMessageAttributes ++
            Map("retryCount" -> 1).toMessageAttributes
          )
          .applyLens(attributes).set(Map.empty)

      ReEnqueuedNotDeletedError(
        updatedMessage,
        Left(UhOh(retrieved)),
        OpFailure(retrieved.message, Left(EntryError("1", "You failed!")))).asLeft
    }
  }

  it should "keep items ordered" in {
    case class Item(i: Int, retrieved: RetrievedMessage)
    implicit val itemContainsMessage = new ContainsMessage[Item] {
      def getMessage(item: Item) = item.retrieved.message
    }
    implicit val itemRecievedTime = new RetrievedTime[Item] {
      def getRetrievedTime(item: Item) = implicitly[RetrievedTime[RetrievedMessage]].getRetrievedTime(item.retrieved)
    }
    val messagesReceived = Promise[List[Message]]()
    val sqsRetry = mockSqsRetry(
      enqueueMessages = messages => {
        messagesReceived.success(messages)
        Future.successful(Right(SendMessageBatchResponse.builder().successful(
          messages.map(m => SendMessageBatchResultEntry.builder().id(m.messageId).build()):_*).build()
        ))
      }
    )


    val retrieved = (id: Int) => RetrievedMessage(newMessage().toBuilder().messageId(id.toString).build(), Instant.now)
    val items = List[Either[Examples, Item]](
      Right(Item(1, retrieved(1))),
      Right(Item(2, retrieved(2))),
      Left(NumberedKerplow(3, retrieved(3))),
      Right(Item(4, retrieved(4))),
      Left(NumberedKerplow(5, retrieved(5))),
      Right(Item(6, retrieved(6))),
      Left(NumberedKerplow(7, retrieved(7))),
      Right(Item(8, retrieved(8)))
    )

    val flow = sqsRetry.flow[Either[Examples, Item]](
      retry = { (in: Either[Examples, Item]) =>
        in match {
          case Left(NumberedKerplow(i, retrieved)) =>
            Map("kerplow" -> s"kerplow-$i").toMessageAttributes
        }
      }
    )

    val pipeline = Source(items)
      .via(flow)
      .runWith(Sink.seq)

    whenReady(pipeline) { returned =>
      returned.collect {
        case Right(Right(Item(i, _))) => i
        case Left(ReEnqueued(_, Left(NumberedKerplow(i, _)))) => i
        case _ => 0
      } should be(1 to 8)
    }
  }

  it should "do nothing with timed-out messages" in {
    val sqsRetry = mockSqsRetry(
      enqueueMessages = messages => {
        if (messages.nonEmpty) {
          Future.failed(new Exception("Unexpected call to enqueue message"))
        } else {
          Future.successful(Right(SendMessageBatchResponse.builder.build()))
        }
      },
      deleteMessages = messages => {
        if (messages.nonEmpty) {
          Future.failed(new Exception("Unexpected call to delete message"))
        } else {
          Future.successful(BatchResult(DeleteMessageBatchResponse.builder.build(), Nil, Nil))
        }
      },
      deadLetterEnqueueMessage = messages => {
        if (messages.nonEmpty) {
          Future.failed(new Exception("Unexpected call to dead letter"))
        } else {
          Future.successful(Right(SendMessageBatchResponse.builder.build()))
        }
      }
    )

    val retrieved = RetrievedMessage(Message.builder.build(), currentTime.minusSeconds(30))

    val flow = sqsRetry.flow[Either[Examples, Examples]](
      retry = { (in: Either[Examples, Examples]) =>
        in match {
          case Left(UhOh(retrieved)) => SqsRetry.emptyAttributes
        }
      }
    )

    val pipeline = Source.single(Left(UhOh(retrieved)))
      .via(flow)
      .runWith(Sink.head)

    whenReady(pipeline) { result =>
      result.left.value shouldBe a[MessageProcessingTimedOut[_]]
    }
  }

  behavior of "SqsRetry Delays"

  it should "doubleOr to a default" in {
    val requestEntry = SendMessageBatchRequestEntry.builder.build()

    SqsRetry.Delays.doubleOr(3, 100)(requestEntry) shouldBe 3
  }

  it should "doubleOr to a doubled value" in {
    val requestEntry = SendMessageBatchRequestEntry.builder()
      .delaySeconds(1)
      .build()

    SqsRetry.Delays.doubleOr(3, 100)(requestEntry) shouldBe 2
  }

  it should "doubleOr to the max when doubling would be too much" in {
    val requestEntry = SendMessageBatchRequestEntry.builder()
      .delaySeconds(75)
      .build()

    SqsRetry.Delays.doubleOr(3, 100)(requestEntry) shouldBe 100
  }

  it should "doubleOr set to default when you accidently put in a negative" in {
    val requestEntry = SendMessageBatchRequestEntry.builder()
      .delaySeconds(-1)
      .build()

    SqsRetry.Delays.doubleOr(3, 100)(requestEntry) shouldBe 3
  }

  it should "constant to a constant value" in {
    val requestEntry = SendMessageBatchRequestEntry.builder()
      .delaySeconds(1)
      .build()

    SqsRetry.Delays.constant(3)(requestEntry) shouldBe 3
  }

  it should "increment to an incremented value" in {
    val requestEntry = SendMessageBatchRequestEntry.builder()
      .delaySeconds(1)
      .build()

    SqsRetry.Delays.increment(2)(requestEntry) shouldBe 3
  }

  it should "increment a negative value to an incremented value" in {
    val requestEntry = SendMessageBatchRequestEntry.builder()
      .delaySeconds(-1)
      .build()

    SqsRetry.Delays.increment(2)(requestEntry) shouldBe 1
  }

}
