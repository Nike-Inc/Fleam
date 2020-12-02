package com.nike.fleam
package sqs

import software.amazon.awssdk.services.sqs.model._
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
      Message.builder().messageId(entry.id).body(entry.body).build()
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 10.seconds, interval = 10.millis)

  val unexpectedSendSingleMessage: SqsEnqueue.Single = { _ => throw new Exception("Unpexected call to send single message") }
  val unexpectedSendBatchMessage: SqsEnqueue.Batch = { _ => throw new Exception("Unpexected call to send batch message") }

  it should "enqueue messages in batch to Sqs" in {
    val url = "http://test/queue"

    val entries = List(
      SendMessageBatchResultEntry.builder().messageId("foo").build(),
      SendMessageBatchResultEntry.builder().messageId("bar").build()
    )

    val response = SendMessageBatchResponse.builder()
      .successful(entries: _*)
      .build()

    val batchSender: SqsEnqueue.Batch = (request: SendMessageBatchRequest) => {
      val expected = SendMessageBatchRequest.builder()
        .queueUrl(url)
        .entries(
          SendMessageBatchRequestEntry.builder().id("foo").messageBody("foo-body").build(),
          SendMessageBatchRequestEntry.builder().id("bar").messageBody("bar-body").build())
        .build()
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

    whenReady(result) { _ shouldBe Right(SendMessageBatchResponse.builder.build()) }
  }

  it should "let you send a single message" in {
    val url = "http://test/queue"

    val requestSent = Promise[SendMessageRequest]()

    val message = Message.builder().build()

    val sendMessage: SendMessageRequest => Future[SendMessageResponse] = { request =>
      requestSent.success(request)
      Future.successful(SendMessageResponse.builder().build())
    }

    val enqueuer = new SqsEnqueue(
      enqueueMessage = sendMessage,
      enqueueMessageBatch = unexpectedSendBatchMessage
    )

    enqueuer.forQueue(url).single(message)

    requestSent.future.futureValue shouldBe {
      SendMessageRequest.builder()
        .queueUrl(url)
        .build()
    }
  }

  it should "allow you to delay messages" in {
    val batchRequest = Promise[SendMessageBatchRequest]()

    val url = "http://test/queue"

    val batchSender: SqsEnqueue.Batch = { request: SendMessageBatchRequest =>
      batchRequest.success(request)
      Future.successful(SendMessageBatchResponse.builder.build())
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
      val entries = request.entries.asScala
      entries.length should be(2)
      entries(0).delaySeconds should be(10)
      entries(1).delaySeconds should be(10)
    }
  }

  it should "store all message values in the SendMessageBatchRequest" in {

    val url = "http://test/queue"

    val attributes = Map("retryCount" -> MessageAttributeValue.builder().dataType("Number").stringValue("0").build()).asJava

    val messages = List(
      Message.builder().messageId("foo").body("foo-body").messageAttributes(attributes).build(),
      Message.builder().messageId("bar").body("bar-body").messageAttributes(attributes).build()
    )

    val entries = List(
      SendMessageBatchRequestEntry.builder()
        .id("foo")
        .messageBody("foo-body")
        .messageAttributes(attributes)
        .delaySeconds(10)
        .build(),

      SendMessageBatchRequestEntry.builder()
        .id("bar")
        .messageBody("bar-body")
        .messageAttributes(attributes)
        .delaySeconds(10)
        .build()
    )

    val batchRequest = Promise[SendMessageBatchRequest]()

    val batchSender: SqsEnqueue.Batch = { request: SendMessageBatchRequest =>
      batchRequest.success(request)
      Future.successful(SendMessageBatchResponse.builder().build())
    }

    val enqueuer = new SqsEnqueue(
      enqueueMessageBatch = batchSender,
      enqueueMessage = unexpectedSendSingleMessage,
      modifyBatchRequest = SqsEnqueue.delayMessagesBySeconds(_ => 10))

    enqueuer.forQueue(url).batched(messages)

    batchRequest.future.futureValue shouldBe {
      SendMessageBatchRequest.builder()
        .entries(entries.asJava)
        .queueUrl(url)
        .build()
    }
  }

  it should "provide a message dedupe id randomize uuid modifier" in {

    val url = "http://test/queue"

    val attributes = Map("retryCount" -> MessageAttributeValue.builder().dataType("Number").stringValue("0").build()).asJava

    val request = SendMessageBatchRequest.builder()
      .entries(List(
        SendMessageBatchRequestEntry.builder()
          .id("foo")
          .messageBody("foo-body")
          .messageAttributes(attributes)
          .delaySeconds(10)
          .build(),

        SendMessageBatchRequestEntry.builder()
          .id("bar")
          .messageBody("bar-body")
          .messageAttributes(attributes)
          .delaySeconds(10)
          .build()
        ).asJava)
      .queueUrl(url)
      .build()

    val modifiedRequest = SqsEnqueue.BatchModifications.randomizeMessageDedupeIds(request)

    val deduplicationIds: List[java.util.UUID] = modifiedRequest.entries.asScala.map { entry =>
      java.util.UUID.fromString(entry.messageDeduplicationId)
    }.toList

    deduplicationIds should have (
      length (2) (of[List[java.util.UUID]])
    )
  }

}
