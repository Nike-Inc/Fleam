package com.nike.fleam
package sqs

import akka.stream.scaladsl._
import configuration.SqsProcessingConfiguration
import com.nike.fleam.configuration.GroupedWithinConfiguration
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import com.amazonaws.services.sqs.model._
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import implicits._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class SqsDeleteTest extends AnyFlatSpec with Matchers with ScalaFutures with IntegrationPatience {

  behavior of "SqsDelete"

  import TestTools._

  val unexpectedDeleteMessage: SqsDelete.Single = { _ => throw new Exception("Unexpected call to delete single message") }
  val unexpectedDeleteMessageBatch: SqsDelete.Batch = { _ => throw new Exception("Unexpected call to delete batch message") }

  val config = SqsProcessingConfiguration(
    parallelism = 1,
    groupedWithin = GroupedWithinConfiguration(
      batchSize = 10,
      within = 1.seconds
    )
  )

  it should "make a request to SQS to delete" in {
    val url = "http://test/queue"

    val messages = List(
      new Message().withMessageId("30").withReceiptHandle("30-receipt"),
      new Message().withMessageId("31").withReceiptHandle("31-receipt"))

    val entries = List(
      new DeleteMessageBatchResultEntry().withId("0"),
      new DeleteMessageBatchResultEntry().withId("1"))

    val deleteMessageBatchResult = new DeleteMessageBatchResult().withSuccessful(entries: _*)

    val response = BatchResult(
      deleteMessageBatchResult = deleteMessageBatchResult,
      failed = Nil,
      successful = List(
        SuccessfulResult(messages.head, entries.head),
        SuccessfulResult(messages(1), entries(1))
      )
    )

    val deleteBatch: SqsDelete.Batch = (request: DeleteMessageBatchRequest) => {
      val expected = new DeleteMessageBatchRequest()
        .withQueueUrl(url)
        .withEntries(new DeleteMessageBatchRequestEntry().withId("0").withReceiptHandle("30-receipt"))
        .withEntries(new DeleteMessageBatchRequestEntry().withId("1").withReceiptHandle("31-receipt"))
      request should be(expected)
      Future.successful(deleteMessageBatchResult)
    }

    val source = Source(messages)

    val result: Future[Seq[BatchResult[Message]]] =
      source
        .via(new SqsDelete(
          deleteMessageBatch = deleteBatch,
          deleteMessage = unexpectedDeleteMessage).forQueue(url).toFlow[Message](config)
        )
        .runWith(Sink.seq)

    whenReady(result) { _ shouldBe Seq(response) }

  }

  it should "not make a request for an empty list" in {
    val url = "http://test/queue"

    val messages = List.empty[Message]

    val source = Source.single(messages)

    val delete: List[Message] => Future[BatchResult[Message]] = new SqsDelete(
      deleteMessage = unexpectedDeleteMessage,
      deleteMessageBatch = unexpectedDeleteMessageBatch
    ).forQueue(url).batched[Message]

    val result = source.mapAsync(1)(delete).runWith(Sink.head)

    whenReady(result) { _ shouldBe BatchResult(new DeleteMessageBatchResult(), Nil, Nil) }
  }

  it should "let you delete a single message" in {
    val url = "http://test/queue"

    val request = Promise[DeleteMessageRequest]()

    val message = new Message().withMessageId("0").withReceiptHandle("30-receipt")

    new SqsDelete(
      deleteMessage = { message => request.success(message); Future.successful(new DeleteMessageResult()) },
      deleteMessageBatch = unexpectedDeleteMessageBatch
    ).forQueue(url).single(message)

    request.future.futureValue shouldBe {
      new DeleteMessageRequest()
        .withQueueUrl(url)
        .withReceiptHandle("30-receipt")
    }
  }

}
