package com.nike.fleam
package sqs

import akka.stream.scaladsl.Flow
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model._
import com.nike.fleam.sqs.configuration.{Default, SqsProcessingConfiguration}
import com.nike.fawcett.sqs._
import monocle.function.all._
import scala.concurrent.{ExecutionContext, Future}

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

sealed trait SqsEnqueueError
case class TooManyMessagesInBatch(size: Int) extends SqsEnqueueError

object SqsEnqueue {
  type Batch = SendMessageBatchRequest => Future[SendMessageBatchResponse]
  type Single = SendMessageRequest => Future[SendMessageResponse]

  def delayMessagesBySeconds(f: SendMessageBatchRequestEntry => Int): SendMessageBatchRequest => SendMessageBatchRequest =
    SendMessageBatchRequestLens.entries composeTraversal each modify { entry: SendMessageBatchRequestEntry =>
      SendMessageBatchRequestEntryLens.delaySeconds.set(f(entry))(entry)
    }

  object BatchModifications {
    val randomizeMessageDedupeIds: SendMessageBatchRequest => SendMessageBatchRequest =
      SendMessageBatchRequestLens.entries composeTraversal each composeLens
      SendMessageBatchRequestEntryLens.messageDeduplicationId set java.util.UUID.randomUUID.toString
  }

  def apply(
    client: SqsAsyncClient,
    modifyRequest: SendMessageRequest => SendMessageRequest = identity,
    modifyBatchRequest: SendMessageBatchRequest => SendMessageBatchRequest = identity) = {
    new SqsEnqueue(
      enqueueMessageBatch = wrapRequest(client.sendMessageBatch),
      enqueueMessage = wrapRequest(client.sendMessage),
      modifyRequest = modifyRequest,
      modifyBatchRequest = modifyBatchRequest)
  }
}

class SqsEnqueue(
  enqueueMessageBatch: SqsEnqueue.Batch,
  modifyBatchRequest: SendMessageBatchRequest => SendMessageBatchRequest = identity,
  enqueueMessage: SqsEnqueue.Single,
  modifyRequest: SendMessageRequest => SendMessageRequest = identity) {

  import ToMessage.ops._

  class UrlFixedQueue(url: String) {
    def batched[T: ToMessage](ts: List[T])(implicit ec: ExecutionContext): Future[Either[SqsEnqueueError, SendMessageBatchResponse]] =
    ts.size match {
      case size if size > 10 => Future.successful(Left(TooManyMessagesInBatch(size)))
      case size if size == 0 => Future.successful(Right(SendMessageBatchResponse.builder.build()))
      case _ => {
        val request = prepareBatchRequest(url, ts)
        enqueueMessageBatch(request).map(Right(_))
      }
    }
    def single[T: ToMessage](t: T): Future[SendMessageResponse] = {
      val request = prepareRequest(url, t)
      enqueueMessage(request)
    }
    def asFlow[T: ToMessage](sqsProcessing: SqsProcessingConfiguration = Default.Sqs.enqueueConfig)
      (implicit ec: ExecutionContext): Flow[T, (List[T], SendMessageBatchResponse), akka.NotUsed] =
      Flow[T]
        .via(sqsProcessing.groupedWithin.toFlow)
        .mapAsync(sqsProcessing.parallelism) { ts =>
          enqueueMessageBatch(prepareBatchRequest(url, ts)).map((ts.toList, _))
        }
  }

  def prepareRequest[T: ToMessage](url: String, t: T): SendMessageRequest = {
    val message = t.toMessage
    val attributes = MessageLens.attributes.get(message)

    import com.nike.fawcett.sqs.SendMessageRequestLens.{messageDeduplicationId, messageGroupId}

    val setMessageDeduplicationId =
      attributes.get(MessageSystemAttributeName.MESSAGE_DEDUPLICATION_ID)
        .fold[SendMessageRequest => SendMessageRequest](identity)(messageDeduplicationId.set)

    val setMessageGrougeId =
      attributes.get(MessageSystemAttributeName.MESSAGE_GROUP_ID)
        .fold[SendMessageRequest => SendMessageRequest](identity)(messageGroupId.set)

    (setMessageDeduplicationId andThen setMessageGrougeId andThen modifyRequest) {
      SendMessageRequest.builder()
          .queueUrl(url)
          .messageBody(message.body)
          .messageAttributes(message.messageAttributes)
          .build()
    }
  }

  private def prepareBatchRequest[T: ToMessage](url: String, ts: Seq[T]): SendMessageBatchRequest = {
    import com.nike.fawcett.sqs.SendMessageBatchRequestEntryLens.{messageDeduplicationId, messageGroupId}
    val entries = ts
      .map { t =>
        val message = t.toMessage
        val attributes = MessageLens.attributes.get(message)
        val setMessageDeduplicationId =
          attributes.get(MessageSystemAttributeName.MESSAGE_DEDUPLICATION_ID)
            .fold[SendMessageBatchRequestEntry => SendMessageBatchRequestEntry](identity)(messageDeduplicationId.set)

        val setMessageGrougeId =
          attributes.get(MessageSystemAttributeName.MESSAGE_GROUP_ID)
            .fold[SendMessageBatchRequestEntry => SendMessageBatchRequestEntry](identity)(messageGroupId.set)

        (setMessageDeduplicationId andThen setMessageGrougeId) {
          SendMessageBatchRequestEntry.builder()
            .id(message.messageId)
            .messageBody(message.body)
            .messageAttributes(message.messageAttributes)
            .build()
        }
      }

    val request = SendMessageBatchRequest.builder()
      .queueUrl(url)
      .entries(entries: _*)
      .build()

    modifyBatchRequest(request)
  }

  def forQueue(url: String) = new UrlFixedQueue(url)
}
