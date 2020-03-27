package com.nike.fleam
package sqs

import java.time.Instant

import akka.stream.scaladsl.Flow
import cats.data._
import cats.implicits._
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model._
import configuration._
import implicits._
import com.nike.fleam.implicits._
import com.nike.fawcett.sqs._
import monocle.function.all._
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

case class OpFailure(message: Message, error: OpError)

sealed trait SqsRetryError[In] {
  val message: Message
  val in: In
}

case class CountError[In](message: Message, in: In, error: MessageCountError) extends SqsRetryError[In]
case class ReEnqueued[In](message: Message, in: In) extends SqsRetryError[In]
case class DeadLettered[In](message: Message, in: In) extends SqsRetryError[In]
case class ExceededRetriesDeadLettered[In](message: Message, in: In, maxRetries: Int) extends SqsRetryError[In]
case class RetryEnqueueError[In](message: Message, in: In, error: OpFailure) extends SqsRetryError[In]
case class ReEnqueuedNotDeletedError[In](message: Message, in: In, error: OpFailure) extends SqsRetryError[In]
case class ExceededRetriesNotDeletedError[In](message: Message, in: In, error: OpFailure) extends SqsRetryError[In]
case class RetryDlqError[In](message: Message, in: In, error: OpFailure) extends SqsRetryError[In]
case class DlqedNotDeletedError[In](message: Message, in: In, error: OpFailure) extends SqsRetryError[In]
case class MessageProcessingTimedOut[In](message: Message, in: In) extends SqsRetryError[In]
case class NoAwsResponse[In](message: Message, in: In) extends SqsRetryError[In]

object SqsRetry {
  val emptyAttributes = Map.empty[String, MessageAttributeValue]

  type RetryResult[T] = Either[SqsRetryError[T], T]
  type RetryFlow[T] = Flow[T, RetryResult[T], akka.NotUsed]

  implicit val messageToMessage: ToMessage[Message] = ToMessage.lift(identity)

  def randomizeMessageDedupeId(uuid: () => java.util.UUID = () => java.util.UUID.randomUUID()):
    Map[String, String] => Map[String, String] =
    _ ++ Map(Attributes.MessageDeduplicationId -> uuid().toString)

  object Delays {
    // For scala 2.11, can remove this after 2.11 support is dropped and use Option instead
    private def getCurrent(requestEntry: SendMessageBatchRequestEntry): Int = {
      val delaySeconds = requestEntry.getDelaySeconds
      if (delaySeconds == null) 0 else delaySeconds
    }

    /** Doubles the current value up to max or sets it to the default if zero, negative, or undefined */
    def doubleOr(seconds: Int, max: Int): SendMessageBatchRequestEntry => Int = { requestEntry =>
      val current = getCurrent(requestEntry)
      if (current > 0) Math.min(current * 2, max) else seconds
    }

    //** Sets a constant delay in seconds */
    def constant(seconds: Int): SendMessageBatchRequestEntry => Int = { _ => seconds }

    //** Increments by a constant number of seconds */
    def increment(seconds: Int): SendMessageBatchRequestEntry => Int = { requestEntry =>
      val current = getCurrent(requestEntry)
      current + seconds
    }
  }

  def apply(
      client: AmazonSQSAsync,
      config: SqsRetryConfiguration,
      delaySeconds: SendMessageBatchRequestEntry => Int = Delays.constant(0))(
      implicit ec: ExecutionContext) =
    new SqsRetry(
      reEnqueueMessages = (messages, ec) =>
        SqsEnqueue(client, modifyBatchRequest = SqsEnqueue.delayMessagesBySeconds(delaySeconds))
          .forQueue(config.queue.url)
          .batched(messages)(implicitly[ToMessage[Message]], ec),
      deleteMessages = SqsDelete(client).forQueue(config.queue.url).batched,
      deadLetterEnqueueMessages = (messages, ec) =>
        SqsEnqueue(client)
          .forQueue(config.deadLetterQueue.url)
          .batched(messages)(implicitly[ToMessage[Message]], ec),
      sqsProcessing = config.sqsProcessing,
      maxRetries = config.maxRetries,
      retryCountKey = config.retryCountKey,
      timeout = config.timeout,
      now = () => Instant.now)
}

class SqsRetry(
    reEnqueueMessages: (List[Message], ExecutionContext) => Future[Either[SqsEnqueueError, SendMessageBatchResult]],
    deleteMessages: List[Message] => Future[BatchResult[Message]],
    deadLetterEnqueueMessages: (List[Message], ExecutionContext) => Future[Either[SqsEnqueueError, SendMessageBatchResult]],
    sqsProcessing: SqsProcessingConfiguration,
    maxRetries: Int,
    timeout: Duration,
    retryCountKey: String,
    now: () => Instant) {

  implicit val messageContainsCount = MessageAttributes.count(retryCountKey)

  def flow[In: ContainsMessage : RetrievedTime](
      retry: PartialFunction[In, Map[String, MessageAttributeValue]],
      deadLetter: PartialFunction[In, Map[String, MessageAttributeValue]] = PartialFunction.empty,
      attributesModifier: Map[String, String] => Map[String, String] = identity,
      retryCountOverrides: PartialFunction[In, Int] = PartialFunction.empty)
      (implicit ec: ExecutionContext): Flow[In, Either[SqsRetryError[In], In], akka.NotUsed] = {

    sealed trait Result
    case class Ok(in: In) extends Result
    case class ReEnqueue(message: Message, in: In) extends Result
    case class DeadLetter(message: Message, in: In) extends Result
    case class ExceededRetries(message: Message, in: In) extends Result
    case class ElapsedTimeout(message: Message, in: In) extends Result

    def resolveRetryCount(in: In): Int = retryCountOverrides.applyOrElse(in, (_: In) => maxRetries)

    val identifyStatus: In => Either[SqsRetryError[In], Result] = { in =>
      val message = in.getMessage

      val deadLetterQualifier: PartialFunction[In, Either[SqsRetryError[In], Result]] =
        deadLetter andThen { messageAttributes =>

          val updatedMessage = MessageLens.messageAttributes.modify(_ ++ messageAttributes)(message)

          Right(DeadLetter(updatedMessage, in))
        }

      val retryQualifier: PartialFunction[In, Either[SqsRetryError[In], Result]] =
        retry andThen { messageAttributes =>
          for {
            count <- message.getCount.leftMap(CountError(message, in, _))
          } yield {

            val updatedMessage =
              (MessageLens.attributes.modify(attributesModifier) andThen
              MessageLens.messageAttributes.modify(_ ++ messageAttributes))(message)

            val elapsed = in.getElaspsedTime(now())

            val availableRetries = resolveRetryCount(in)

            if (elapsed >= timeout) {
              ElapsedTimeout(updatedMessage, in)
            } else if (count > availableRetries) {
              ExceededRetries(updatedMessage, in)
            } else {
              ReEnqueue(updatedMessage.setCount(count + 1), in)
            }
          }
        }

      (deadLetterQualifier orElse retryQualifier)
        .applyOrElse[In, Either[SqsRetryError[In], Result]](in, (_: In) => Right(Ok(in)))
    }

    type SendBatch = (List[Message], ExecutionContext) => Future[Either[SqsEnqueueError, SendMessageBatchResult]]
    val sendPartitioned = (messages: List[Message], send: SendBatch) => {
      val messageMap = messages.map(m => (m.getMessageId, m)).toMap
      EitherT(send(messages, ec)).fold(
        enqueueError => messages.map(message => OpFailure(message, enqueueError.asRight).asLeft[Message]),
        batchResult => {
          val successes = (SendMessageBatchResultLens.successful composeTraversal each
            composeLens SendMessageBatchResultEntryLens.id)
            .getAll(batchResult)
            .flatMap(messageMap.get)
            .map(_.asRight[OpFailure])

          val failures = SendMessageBatchResultLens.failed.get(batchResult)
            .flatMap { failure =>
              messageMap.get(failure.getId).map(
                message => OpFailure(message, EntryError(failure.getCode, failure.getMessage).asLeft)
                  .asLeft[Message]
              )
            }

          failures ++ successes
        }
      )
    }

    type DeleteBatch = List[Message] => Future[BatchResult[Message]]
    val deletePartitioned = (messages: List[Message], delete: DeleteBatch) => {
      val messageMap = messages.map(m => (m.getMessageId, m)).toMap
      delete(messages).map(batchResult => {
        val successes = batchResult.successful.map(_.entry.getId).flatMap(messageMap.get)
        val failures = batchResult.failed.map(failure =>
          OpFailure(failure.entity, EntryError(failure.entry.getCode, failure.entry.getMessage).asLeft).asLeft[Message]
        )
        failures ++ successes.map(Right(_))
      })
    }

    val reEnqueueOrDeadLetter: Seq[Either[SqsRetryError[In], Result]] => Future[List[Either[SqsRetryError[In], In]]] = {
      retryOrIns =>
        val indexedRetries: List[(Either[SqsRetryError[In], Result], Int)] = retryOrIns.zipWithIndex.toList

        val messagesToRetry = indexedRetries.collect { case (Right(ReEnqueue(message, _)), _) => message }
        val messagesToDL = indexedRetries.collect {
          case (Right(DeadLetter(message, _)), _) => message
          case (Right(ExceededRetries(message, _)), _) => message
        }

        type SqsOpResults = Future[List[Either[OpFailure, Message]]]
        val reEnqueue: SqsOpResults = sendPartitioned(messagesToRetry, reEnqueueMessages)
        val dlMessages: SqsOpResults = sendPartitioned(messagesToDL, deadLetterEnqueueMessages)

        def findMessageResult[E](results: List[Either[OpFailure, Message]])(targetMessage: Message): Option[Either[OpFailure, Message]] = {
          results.find {
            case Left(opFailure: OpFailure) => opFailure.message.getMessageId == targetMessage.getMessageId
            case Right(message) => message.getMessageId == targetMessage.getMessageId
          }
        }


        for {
          retryResults <- reEnqueue
          dlResults <- dlMessages
          results = retryResults ++ dlResults
          successes = results.collect { case Right(message) => message }
          deleteResults <- deletePartitioned(successes, deleteMessages)
          successfulDeletes = deleteResults.collect { case Right(message) => message }
        } yield {

          def createResponse(
            message: Message,
            in: In,
            success: (Message, In) => SqsRetryError[In],
            deleteFailed: (Message, In, OpFailure) => SqsRetryError[In],
            enqueueFailed: (Message, In, OpFailure) => SqsRetryError[In]): Either[SqsRetryError[In], In] =
              findMessageResult(results)(message).map[SqsRetryError[In]] {
                case Right(message) => EitherT(findMessageResult(deleteResults)(message))
                  .fold(deleteFailed(message, in, _), _ => success(message, in)).getOrElse(NoAwsResponse(message, in))
                case Left(failure) => enqueueFailed(message, in, failure)
              }.getOrElse(NoAwsResponse(message, in)).asLeft[In]

          indexedRetries.map(_.swap).toMap.view.map { case (index, result) =>
            val response = result match {
              case Right(ReEnqueue(message, in)) =>
                createResponse(
                  message,
                  in,
                  success = ReEnqueued.apply,
                  deleteFailed = ReEnqueuedNotDeletedError.apply,
                  enqueueFailed = RetryEnqueueError.apply)

              case Right(DeadLetter(message, in)) =>
                createResponse(
                  message,
                  in,
                  success = DeadLettered.apply,
                  deleteFailed = DlqedNotDeletedError.apply,
                  enqueueFailed = RetryDlqError.apply
                )

              case Right(ExceededRetries(message, in)) =>
                createResponse(
                  message,
                  in,
                  success = ExceededRetriesDeadLettered.apply(_, _, resolveRetryCount(in)),
                  deleteFailed = ExceededRetriesNotDeletedError.apply,
                  enqueueFailed = RetryDlqError.apply
                )

              case Right(ElapsedTimeout(message, in)) => Left(MessageProcessingTimedOut(message, in))
              case Right(Ok(in)) => Right(in)
              case Left(error) => Left(error)
            }
            (index, response)
          }
          .toList
          .sortBy(_._1)
          .map { case (_, result) => result }
        }
    }

    Flow[In]
      .map(identifyStatus)
      .via(sqsProcessing.groupedWithin.toFlow)
      .mapAsync(sqsProcessing.parallelism)(reEnqueueOrDeadLetter)
      .mapConcat(identity)
  }
}
