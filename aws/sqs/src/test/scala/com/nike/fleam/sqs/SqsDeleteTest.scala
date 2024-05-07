package com.nike.fleam
package sqs

import org.apache.pekko.stream.scaladsl._
import configuration.SqsProcessingConfiguration
import com.nike.fleam.configuration.GroupedWithinConfiguration
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import software.amazon.awssdk.services.sqs.model._
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
      Message.builder().messageId("30").receiptHandle("30-receipt").build(),
      Message.builder().messageId("31").receiptHandle("31-receipt").build())

    val entries = List(
      DeleteMessageBatchResultEntry.builder().id("0").build(),
      DeleteMessageBatchResultEntry.builder().id("1").build())

    val deleteMessageBatchResponse = DeleteMessageBatchResponse.builder().successful(entries: _*).build()

    val response = BatchResult(
      deleteMessageBatchResponse = deleteMessageBatchResponse,
      failed = Nil,
      successful = List(
        SuccessfulResult(messages.head, entries.head),
        SuccessfulResult(messages(1), entries(1))
      )
    )

    val deleteBatch: SqsDelete.Batch = (request: DeleteMessageBatchRequest) => {
      val expected = DeleteMessageBatchRequest.builder()
        .queueUrl(url)
        .entries(
          DeleteMessageBatchRequestEntry.builder().id("0").receiptHandle("30-receipt").build(),
          DeleteMessageBatchRequestEntry.builder().id("1").receiptHandle("31-receipt").build())
        .build()
      request should be(expected)
      Future.successful(deleteMessageBatchResponse)
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

    whenReady(result) { _ shouldBe BatchResult(DeleteMessageBatchResponse.builder.build(), Nil, Nil) }
  }

  it should "let you delete a single message" in {
    val url = "http://test/queue"

    val request = Promise[DeleteMessageRequest]()

    val message = Message.builder().messageId("0").receiptHandle("30-receipt").build()

    new SqsDelete(
      deleteMessage = { message => request.success(message); Future.successful(DeleteMessageResponse.builder.build()) },
      deleteMessageBatch = unexpectedDeleteMessageBatch
    ).forQueue(url).single(message)

    request.future.futureValue shouldBe {
      DeleteMessageRequest.builder()
        .queueUrl(url)
        .receiptHandle("30-receipt")
        .build()
    }
  }

}
