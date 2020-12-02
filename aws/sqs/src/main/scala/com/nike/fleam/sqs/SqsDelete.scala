package com.nike.fleam
package sqs

import com.nike.fleam.configuration._
import sqs.configuration._
import software.amazon.awssdk.services.sqs.model._
import software.amazon.awssdk.services.sqs.SqsAsyncClient
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
  type Batch = DeleteMessageBatchRequest => Future[DeleteMessageBatchResponse]
  type Single = DeleteMessageRequest => Future[DeleteMessageResponse]
  def apply(
    client: SqsAsyncClient,
    modifyRequest: DeleteMessageRequest => DeleteMessageRequest = identity,
    modifyBatchRequest: DeleteMessageBatchRequest => DeleteMessageBatchRequest = identity) = {
    new SqsDelete(
      deleteMessage = wrapRequest(client.deleteMessage),
      deleteMessageBatch = wrapRequest(client.deleteMessageBatch),
      modifyRequest = modifyRequest,
      modifyBatchRequest = modifyBatchRequest
    )
  }
}

case class FailedResult[T](entity: T, entry: BatchResultErrorEntry)
case class SuccessfulResult[T](entity: T, entry: DeleteMessageBatchResultEntry)
case class BatchResult[T](
  deleteMessageBatchResponse: DeleteMessageBatchResponse,
  failed: List[FailedResult[T]],
  successful: List[SuccessfulResult[T]]
)

class SqsDelete(
  deleteMessage: SqsDelete.Single,
  deleteMessageBatch: SqsDelete.Batch,
  modifyRequest: DeleteMessageRequest => DeleteMessageRequest = identity,
  modifyBatchRequest: DeleteMessageBatchRequest => DeleteMessageBatchRequest = identity) {

  class UrlFixedQueue(url: String) {
    def single[T: ContainsMessage](t: T): Future[DeleteMessageResponse] = {
      val request = modifyRequest { DeleteMessageRequest.builder()
        .queueUrl(url)
        .receiptHandle(t.getMessage.receiptHandle)
        .build()
      }
      deleteMessage(request)
    }

    /** Deletes batches of messages
     *
     *  Due to the at-least-once nature of SQS it's possible to have batches with duplicate messages.
     *  SqsDelete will delete these duplicates without notification since the operation is idempotent.
     *
     *  @tparam T Input type that contains a message.
     *  @param ts A list of `T` items to batch delete. Must be 10 or less items. For automatic grouping use `toFlow` instead.
     *  @param ec ExecutionContext
     *  @return BatchResult containing AWS DeleteMessageBatchResponse and results divided into successful and failed.
     */
    def batched[T: ContainsMessage](ts: List[T])(implicit ec: ExecutionContext): Future[BatchResult[T]] = {
      if (ts.isEmpty) {
        Future.successful(BatchResult(DeleteMessageBatchResponse.builder.build(), Nil, Nil))
      } else {
        val indexedTs = ts.zipWithIndex
        val entries = indexedTs.map { case (t, index) =>
          val message = t.getMessage
          DeleteMessageBatchRequestEntry.builder()
            .id(index.toString)
            .receiptHandle(message.receiptHandle)
            .build()
        }

        val request = modifyBatchRequest { DeleteMessageBatchRequest.builder()
          .queueUrl(url)
          .entries(entries: _*)
          .build()
        }

        deleteMessageBatch(request)
          .map { deleteMessageBatchResponse =>
            BatchResult(
              deleteMessageBatchResponse = deleteMessageBatchResponse,
              failed = DeleteMessageBatchResponseLens.failed.get(deleteMessageBatchResponse).map(result =>
                indexedTs.collect { case (t, index) if index.toString == result.id => FailedResult(t, result) }
              ).flatten,
              successful = DeleteMessageBatchResponseLens.successful.get(deleteMessageBatchResponse).map(result =>
                indexedTs.collect { case (t, index) if index.toString == result.id => SuccessfulResult(t, result) }
              ).flatten
            )
          }
      }
    }

    /** Deletes batches of messages
     *
     *  Due to the at-least-once nature of SQS it's possible to have batches with duplicate messages.
     *  SqsDelete will delete these duplicates without notification since the operation is idepempotent.
     *
     *  @tparam T Input type that contains a message.
     *  @param config options for parallelism and batchsize
     *  @param ec ExecutionContext
     *  @return Flow from T to BatchResult containing AWS DeleteMessageBatchResponse and results divided into successful and failed.
     */
    def toFlow[T: ContainsMessage](config: SqsProcessingConfiguration)(implicit ec: ExecutionContext):
        Flow[T, BatchResult[T], akka.NotUsed] =
      Flow[T]
        .via(config.groupedWithin.toFlow)
        .map(_.toList)
        .mapAsync(config.parallelism) { batched[T] }
  }

  def forQueue(url: String): UrlFixedQueue = new UrlFixedQueue(url)
}
