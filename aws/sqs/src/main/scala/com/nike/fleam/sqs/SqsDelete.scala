package com.nike.fleam
package sqs

import com.nike.fleam.configuration._
import sqs.configuration._
import com.amazonaws.services.sqs.model._
import com.amazonaws.services.sqs.AmazonSQSAsync
import akka.stream.scaladsl._
import ContainsMessage.ops._
import com.nike.fawcett.sqs._

import scala.concurrent.{ExecutionContext, Future}

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

object SqsDelete {
  type Batch = DeleteMessageBatchRequest => Future[DeleteMessageBatchResult]
  type Single = DeleteMessageRequest => Future[DeleteMessageResult]
  def apply(
    client: AmazonSQSAsync,
    modifyRequest: DeleteMessageRequest => DeleteMessageRequest = identity,
    modifyBatchRequest: DeleteMessageBatchRequest => DeleteMessageBatchRequest = identity) = {
    new SqsDelete(
      deleteMessage = wrapRequest(client.deleteMessageAsync),
      deleteMessageBatch = wrapRequest(client.deleteMessageBatchAsync),
      modifyRequest = modifyRequest,
      modifyBatchRequest = modifyBatchRequest
    )
  }
}

case class FailedResult[T](entity: T, entry: BatchResultErrorEntry)
case class SuccessfulResult[T](entity: T, entry: DeleteMessageBatchResultEntry)
case class BatchResult[T](
  deleteMessageBatchResult: DeleteMessageBatchResult,
  failed: List[FailedResult[T]],
  successful: List[SuccessfulResult[T]]
)

class SqsDelete(
  deleteMessage: SqsDelete.Single,
  deleteMessageBatch: SqsDelete.Batch,
  modifyRequest: DeleteMessageRequest => DeleteMessageRequest = identity,
  modifyBatchRequest: DeleteMessageBatchRequest => DeleteMessageBatchRequest = identity) {

  class UrlFixedQueue(url: String) {
    def single[T: ContainsMessage](t: T): Future[DeleteMessageResult] = {
      val request = modifyRequest { new DeleteMessageRequest()
        .withQueueUrl(url)
        .withReceiptHandle(t.getMessage.getReceiptHandle)
      }
      deleteMessage(request)
    }

    def batched[T: ContainsMessage](ts: List[T])(implicit ec: ExecutionContext): Future[BatchResult[T]] = {
      if (ts.isEmpty) {
        Future.successful(BatchResult(new DeleteMessageBatchResult(), Nil, Nil))
      } else {
        val entries = ts.map { t =>
          val message = t.getMessage
          new DeleteMessageBatchRequestEntry().withId(message.getMessageId).withReceiptHandle(message.getReceiptHandle)
        }

        val request = modifyBatchRequest { new DeleteMessageBatchRequest()
          .withQueueUrl(url)
          .withEntries(entries: _*)
        }

        deleteMessageBatch(request)
          .map { deleteMessageBatchResult =>
            BatchResult(
              deleteMessageBatchResult = deleteMessageBatchResult,
              failed = DeleteMessageBatchResultLens.failed.get(deleteMessageBatchResult).map(result =>
                ts.find(_.getMessage.getMessageId == result.getId).map(FailedResult(_, result))
              ).flatten,
              successful = DeleteMessageBatchResultLens.successful.get(deleteMessageBatchResult).map(result =>
                ts.find(_.getMessage.getMessageId == result.getId).map(SuccessfulResult(_, result))
              ).flatten
            )
          }
      }
    }

    def toFlow[T: ContainsMessage](config: SqsProcessingConfiguration)(implicit ec: ExecutionContext):
        Flow[T, BatchResult[T], akka.NotUsed] =
      Flow[T]
        .via(config.groupedWithin.toFlow)
        .map(_.toList)
        .mapAsync(config.parallelism) { batched[T] }
  }

  def forQueue(url: String): UrlFixedQueue = new UrlFixedQueue(url)
}
