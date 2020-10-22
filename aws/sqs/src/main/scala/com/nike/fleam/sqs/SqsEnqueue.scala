package com.nike.fleam
package sqs

import akka.stream.scaladsl.Flow
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model._
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
  type Batch = SendMessageBatchRequest => Future[SendMessageBatchResult]
  type Single = SendMessageRequest => Future[SendMessageResult]

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
    client: AmazonSQSAsync,
    modifyRequest: SendMessageRequest => SendMessageRequest = identity,
    modifyBatchRequest: SendMessageBatchRequest => SendMessageBatchRequest = identity) = {
    new SqsEnqueue(
      enqueueMessageBatch = wrapRequest(client.sendMessageBatchAsync),
      enqueueMessage = wrapRequest(client.sendMessageAsync),
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
    def batched[T: ToMessage](ts: List[T])(implicit ec: ExecutionContext): Future[Either[SqsEnqueueError, SendMessageBatchResult]] =
    ts.size match {
      case size if size > 10 => Future.successful(Left(TooManyMessagesInBatch(size)))
      case size if size == 0 => Future.successful(Right(new SendMessageBatchResult()))
      case _ => {
        val request = prepareBatchRequest(url, ts)
        enqueueMessageBatch(request).map(Right(_))
      }
    }
    def single[T: ToMessage](t: T): Future[SendMessageResult] = {
      val request = prepareRequest(url, t)
      enqueueMessage(request)
    }
    def asFlow[T: ToMessage](sqsProcessing: SqsProcessingConfiguration = Default.Sqs.enqueueConfig)
      (implicit ec: ExecutionContext): Flow[T, (List[T], SendMessageBatchResult), akka.NotUsed] =
      Flow[T]
        .via(sqsProcessing.groupedWithin.toFlow)
        .mapAsync(sqsProcessing.parallelism) { ts =>
          enqueueMessageBatch(prepareBatchRequest(url, ts)).map((ts.toList, _))
        }
  }

  def prepareRequest[T: ToMessage](url: String, t: T): SendMessageRequest = {
    val message = t.toMessage
    val attributes = MessageLens.attributes.get(message)
    modifyRequest { new SendMessageRequest {
      setQueueUrl(url)
      setMessageBody(message.getBody)
      setMessageAttributes(message.getMessageAttributes)
      attributes.get(Attributes.MessageDeduplicationId).foreach(setMessageDeduplicationId)
      attributes.get(Attributes.MessageGroupId).foreach(setMessageGroupId)
    }}
  }

  private def prepareBatchRequest[T: ToMessage](url: String, ts: Seq[T]): SendMessageBatchRequest = {
    val entries = ts
      .map { t =>
        val message = t.toMessage
        val attributes = MessageLens.attributes.get(message)
        new SendMessageBatchRequestEntry {
          setId(message.getMessageId)
          setMessageBody(message.getBody)
          setMessageAttributes(message.getMessageAttributes)
          attributes.get(Attributes.MessageDeduplicationId).foreach(setMessageDeduplicationId)
          attributes.get(Attributes.MessageGroupId).foreach(setMessageGroupId)
        }
      }
    val request = new SendMessageBatchRequest()
      .withQueueUrl(url)
      .withEntries(entries: _*)

    modifyBatchRequest(request)
  }

  def forQueue(url: String) = new UrlFixedQueue(url)
}
