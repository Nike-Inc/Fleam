package com.nike.fleam
package sqs

import akka.stream.scaladsl.{Flow, Source}
import sqs.configuration._
import com.nike.fleam.configuration._
import cats.{Order, Semigroup}
import cats.data.{EitherT, NonEmptyList}
import cats.implicits._
import com.amazonaws.services.sqs.model._
import com.amazonaws.services.sqs.AmazonSQSAsync
import scala.concurrent.{ExecutionContext, Future}
import ContainsMessage.ops._
import ToMessage.ops._
import com.nike.fleam.implicits._
import instances.MessageError
import instances.MessageInstances._
import instances.ContainsMessageInstances._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

sealed trait SqsReduceError[Item]
case class Reduced[Item](item: Item, batchOf: Int, enqueueResult: SendMessageResult) extends SqsReduceError[Item]
case class EnqueueError[Item](error: AmazonSQSException, item: Item, reducedMessage: Message) extends SqsReduceError[Item]
case class FailedDelete[Item](batchResult: FailedResult[Message], item: Item) extends SqsReduceError[Item]
case class BadKey[Item](error: MessageError, item: Item) extends SqsReduceError[Item]

object SqsReduce {
  def apply(
    config: SqsReduceConfiguration,
    client: AmazonSQSAsync)(implicit ec: ExecutionContext): SqsReduce =
  new SqsReduce(
    config = config,
    reEnqueueMessages = message =>
      SqsEnqueue(client)
        .forQueue(config.queue.url)
        .single(message)(implicitly[ToMessage[Message]]),
    deleteMessages = SqsDelete(client).forQueue(config.queue.url).batched
  )
}

/** SqsReduce combines several messages with the same Key into a single message
 *
 *  The combined message is enqueued and the original messages are deleted.
 */
class SqsReduce(
    config: SqsReduceConfiguration,
    reEnqueueMessages: Message => Future[SendMessageResult],
    deleteMessages: List[Message] => Future[BatchResult[Message]]
  )(implicit ec: ExecutionContext) {

  def flow[Item : Semigroup : ContainsMessage : ToMessage : Keyed[?, Either[MessageError, Key]], Key : Order]:
      Flow[Item, Either[SqsReduceError[Item], Item], akka.NotUsed] = {
    config.grouping.toFlow[Item]
      .flatMapConcat { group =>
        val (noKeys, keyed) = group
          .toList
          .partitionEither { item =>
            item.getKey.bimap(_ -> item, _ -> item)
          }

        val grouped = keyed
          .groupByNel { case (key, item) => key }
          .view.map { case (key, groupedList) =>
            groupedList.map { case (key, item) => item }.asRight
          }
          .toList

        val errors = noKeys.map { case (error, item) => NonEmptyList.of(BadKey(error, item)).asLeft }

        Source[Either[NonEmptyList[SqsReduceError[Item]], NonEmptyList[Item]]](errors ++ grouped)
      }
      .eitherFlatMapAsyncUnordered(config.deleteParallelism) {
        case NonEmptyList(x, Nil) => Future.successful(NonEmptyList.of(x).asRight)
        case items =>
          val reduced = items.reduce
          val reducedMessage = reduced.toMessage
          (for {
            enqueueResult <- EitherT {
              reEnqueueMessages(reducedMessage)
                .map(_.asRight[NonEmptyList[SqsReduceError[Item]]])
                .recover[Either[NonEmptyList[SqsReduceError[Item]], SendMessageResult]] {
                  case ex: AmazonSQSException => items.map(EnqueueError(ex, _, reducedMessage)).asLeft[SendMessageResult]
                }
            }
            batchResult <- EitherT.right[NonEmptyList[SqsReduceError[Item]]](deleteMessages(items.map(_.getMessage).toList))
          } yield {
            val count = items.length
            items.map[SqsReduceError[Item]] { item =>
              batchResult.failed.find(_.entry.getId == item.getMessage.getMessageId)
                .map(FailedDelete(_, item))
                .getOrElse(Reduced(item, count, enqueueResult))
            }
          })
          .value
          .map(_.merge.asLeft)
      }
      .flatMapConcat(results => Source(results.bisequence.toList))
  }
}
