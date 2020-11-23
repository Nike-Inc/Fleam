package com.nike.fleam
package sqs

import com.amazonaws.services.sqs.model._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import implicits._
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class SqsEnqueueTest extends AnyFlatSpec with Matchers with ScalaFutures {

  behavior of "SqsEnqueue"
  import TestTools._

  case class MessageEntry(id: String, body: String)

  implicit val MessageEntryToMessage = new ToMessage[MessageEntry] {
    def toMessage(entry: MessageEntry) =
      new Message().withMessageId(entry.id).withBody(entry.body)
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 10.seconds, interval = 10.millis)

  val unexpectedSendSingleMessage: SqsEnqueue.Single = { _ => throw new Exception("Unpexected call to send single message") }
  val unexpectedSendBatchMessage: SqsEnqueue.Batch = { _ => throw new Exception("Unpexected call to send batch message") }

  it should "enqueue messages in batch to Sqs" in {
    val url = "http://test/queue"

    val entries = List(
      new SendMessageBatchResultEntry().withMessageId("foo"),
      new SendMessageBatchResultEntry().withMessageId("bar")
    )

    val response = new SendMessageBatchResult()
      .withSuccessful(entries: _*)

    val batchSender: SqsEnqueue.Batch = (request: SendMessageBatchRequest) => {
      val expected = new SendMessageBatchRequest()
        .withQueueUrl(url)
        .withEntries(new SendMessageBatchRequestEntry().withId("foo").withMessageBody("foo-body"))
        .withEntries(new SendMessageBatchRequestEntry().withId("bar").withMessageBody("bar-body"))
      request shouldBe expected
      Future.successful(response)
    }

    val messageEntries = List(
      MessageEntry("foo", "foo-body"),
      MessageEntry("bar", "bar-body")
    )

    val enqueuer = new SqsEnqueue(
      enqueueMessageBatch = batchSender,
      enqueueMessage = unexpectedSendSingleMessage
    )

    val result = enqueuer.forQueue(url).batched(messageEntries)

    whenReady(result) { _ shouldBe Right(response)}
  }

  it should "return TooManyMessagesInBatch error when sending more than 10 messages to Sqs" in {
    val url = "http://test/queue"

    val messageEntries = (1 to 11).map(x => MessageEntry(s"foo-$x", s"foo-body-$x")).toList

    val enqueuer = new SqsEnqueue(
      enqueueMessageBatch = unexpectedSendBatchMessage,
      enqueueMessage = unexpectedSendSingleMessage
    )

    val result = enqueuer.forQueue(url).batched(messageEntries)

    whenReady(result) { _ shouldBe Left(TooManyMessagesInBatch(11))}
  }

  it should "return an empty SendMessageBatchResult when sending empty list to Sqs" in {
    val url = "http://test/queue"

    val messageEntries = List.empty[MessageEntry]

    val enqueuer = new SqsEnqueue(
      enqueueMessageBatch = unexpectedSendBatchMessage,
      enqueueMessage = unexpectedSendSingleMessage
    )

    val result = enqueuer.forQueue(url).batched(messageEntries)

    whenReady(result) { _ shouldBe Right(new SendMessageBatchResult()) }
  }

  it should "let you send a single message" in {
    val url = "http://test/queue"

    val requestSent = Promise[SendMessageRequest]()

    val message = new Message()

    val sendMessage: SendMessageRequest => Future[SendMessageResult] = { request =>
      requestSent.success(request)
      Future.successful(new SendMessageResult())
    }

    val enqueuer = new SqsEnqueue(
      enqueueMessage = sendMessage,
      enqueueMessageBatch = unexpectedSendBatchMessage
    )

    enqueuer.forQueue(url).single(message)

    requestSent.future.futureValue shouldBe {
      new SendMessageRequest()
        .withQueueUrl(url)
    }
  }

  it should "allow you to delay messages" in {
    val batchRequest = Promise[SendMessageBatchRequest]()

    val url = "http://test/queue"

    val batchSender: SqsEnqueue.Batch = { request: SendMessageBatchRequest =>
      batchRequest.success(request)
      Future.successful(new SendMessageBatchResult())
    }

    val messageEntries = List(
      MessageEntry("foo", "foo-body"),
      MessageEntry("bar", "bar-body")
    )

    val enqueuer = new SqsEnqueue(
      enqueueMessageBatch = batchSender,
      enqueueMessage = unexpectedSendSingleMessage,
      modifyBatchRequest = SqsEnqueue.delayMessagesBySeconds(_ => 10))

    val result = enqueuer.forQueue(url).batched(messageEntries)

    whenReady(checkSideEffect(result, batchRequest)) { request: SendMessageBatchRequest =>
      val entries = request.getEntries.asScala
      entries.length should be(2)
      entries(0).getDelaySeconds should be(10)
      entries(1).getDelaySeconds should be(10)
    }
  }

  it should "store all message values in the SendMessageBatchRequest" in {

    val url = "http://test/queue"

    val attributes = Map("retryCount" -> new MessageAttributeValue().withDataType("Number").withStringValue("0")).asJava

    val messages = List(
      new Message().withMessageId("foo").withBody("foo-body").withMessageAttributes(attributes),
      new Message().withMessageId("bar").withBody("bar-body").withMessageAttributes(attributes)
    )

    val entries = List(
      new SendMessageBatchRequestEntry()
        .withId("foo")
        .withMessageBody("foo-body")
        .withMessageAttributes(attributes).withDelaySeconds(10),

      new SendMessageBatchRequestEntry()
        .withId("bar")
        .withMessageBody("bar-body")
        .withMessageAttributes(attributes)
        .withDelaySeconds(10)
    )

    val batchRequest = Promise[SendMessageBatchRequest]()

    val batchSender: SqsEnqueue.Batch = { request: SendMessageBatchRequest =>
      batchRequest.success(request)
      Future.successful(new SendMessageBatchResult())
    }

    val enqueuer = new SqsEnqueue(
      enqueueMessageBatch = batchSender,
      enqueueMessage = unexpectedSendSingleMessage,
      modifyBatchRequest = SqsEnqueue.delayMessagesBySeconds(_ => 10))

    enqueuer.forQueue(url).batched(messages)

    batchRequest.future.futureValue shouldBe {
      new SendMessageBatchRequest()
        .withEntries(entries.asJava)
        .withQueueUrl(url)
    }
  }

  it should "provide a message dedupe id randomize uuid modifier" in {

    val url = "http://test/queue"

    val attributes = Map("retryCount" -> new MessageAttributeValue().withDataType("Number").withStringValue("0")).asJava

    val request = new SendMessageBatchRequest()
      .withEntries(List(
        new SendMessageBatchRequestEntry()
          .withId("foo")
          .withMessageBody("foo-body")
          .withMessageAttributes(attributes).withDelaySeconds(10),

        new SendMessageBatchRequestEntry()
          .withId("bar")
          .withMessageBody("bar-body")
          .withMessageAttributes(attributes)
          .withDelaySeconds(10)
        ).asJava)
      .withQueueUrl(url)

    val modifiedRequest = SqsEnqueue.BatchModifications.randomizeMessageDedupeIds(request)

    val deduplicationIds: List[java.util.UUID] = modifiedRequest.getEntries.asScala.map { entry =>
      java.util.UUID.fromString(entry.getMessageDeduplicationId)
    }.toList

    deduplicationIds should have (
      length (2) (of[List[java.util.UUID]])
    )
  }

}
