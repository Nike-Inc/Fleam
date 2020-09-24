package com.nike.fleam
package sqs

import cats.Show
import cats.implicits._
import com.nike.fleam.configuration._
import sqs.configuration._
import com.amazonaws.services.sqs.model._
import com.amazonaws.services.sqs.AmazonSQSAsync
import akka.stream.scaladsl._
import ContainsMessage.ops._
import com.nike.fawcett.sqs._
import Keyed.ops._

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

    /** Deletes batches of messages
     *
     *  @tparam T Input type that contains a message and provides a key to track deletes.
     *            Due to the at-least-once nature of SQS it's possible to have batches with duplicate messages ids.
     *            Providing an alternate unique id in your type can avoid this problem if desired.
     *            Keyed and ContainsMessage for Message can be imported from com.nike.fleam.sqs.implicits._
     *  @tparam Key Type of Key used for tracking deletes. It must provide an instance of Show.
     *              For Messages use type `MessageId` with the default implicits provided to use the Amazon provided
     *              message id.
     *              Import cats.implicits._ to get a `Show` instance for `MessageId`.
     *  @param ts A list of `T` items to batch delete. Must be 10 or less items. For automatic grouping use `toFlow` instead.
     *  @param ec ExecutionContext
     *  @return BatchResult containing AWS DeleteMessageBatchResult and results divided into successful and failed.
     */
    def batched[T: ContainsMessage : Keyed[?, Key], Key : Show](ts: List[T])(implicit ec: ExecutionContext): Future[BatchResult[T]] = {
      if (ts.isEmpty) {
        Future.successful(BatchResult(new DeleteMessageBatchResult(), Nil, Nil))
      } else {
        val entries = ts.map { t =>
          val message = t.getMessage
          new DeleteMessageBatchRequestEntry().withId(t.getKey.show).withReceiptHandle(message.getReceiptHandle)
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
                ts.find(_.getKey.show == result.getId).map(FailedResult(_, result))
              ).flatten,
              successful = DeleteMessageBatchResultLens.successful.get(deleteMessageBatchResult).map(result =>
                ts.find(_.getKey.show == result.getId).map(SuccessfulResult(_, result))
              ).flatten
            )
          }
      }
    }

    /** Deletes batches of messages
     *
     *  @tparam T Input type that contains a message and provides a key to track deletes.
     *            Due to the at-least-once nature of SQS it's possible to have batches with duplicate messages ids.
     *            Providing an alternate unique id in your type can avoid this problem if desired.
     *            Keyed and ContainsMessage for Message can be imported from com.nike.fleam.sqs.implicits._
     *  @tparam Key Type of Key used for tracking deletes. It must provide an instance of Show.
     *              For Messages use type `MessageId` with the default implicits provided to use the Amazon provided
     *              message id.
     *              Import cats.implicits._ to get a `Show` instance for `MessageId`.
     *  @param config options for parallelism and batchsize
     *  @param ec ExecutionContext
     *  @return Flow from T to BatchResult containing AWS DeleteMessageBatchResult and results divided into successful and failed.
     */
    def toFlow[T: ContainsMessage : Keyed[?, Key], Key: Show](config: SqsProcessingConfiguration)(implicit ec: ExecutionContext):
        Flow[T, BatchResult[T], akka.NotUsed] =
      Flow[T]
        .via(config.groupedWithin.toFlow)
        .map(_.toList)
        .mapAsync(config.parallelism) { batched[T, Key] }
  }

  def forQueue(url: String): UrlFixedQueue = new UrlFixedQueue(url)
}
