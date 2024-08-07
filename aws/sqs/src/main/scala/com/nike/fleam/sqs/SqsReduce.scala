package com.nike.fleam
package sqs

import org.apache.pekko.stream.scaladsl.{Flow, Source}
import sqs.configuration._
import com.nike.fleam.configuration._
import cats.{Order, Semigroup}
import cats.data.{EitherT, NonEmptyList}
import cats.implicits._
import software.amazon.awssdk.services.sqs.model._
import software.amazon.awssdk.services.sqs.SqsAsyncClient
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
case class Reduced[Item](item: Item, batchOf: Int, enqueueResponse: SendMessageResponse) extends SqsReduceError[Item]
case class EnqueueError[Item](error: SqsException, item: Item, reducedMessage: Message) extends SqsReduceError[Item]
case class FailedDelete[Item](batchResult: FailedResult[Message], item: Item) extends SqsReduceError[Item]
case class MissingKey[Item](error: MessageError, item: Item) extends SqsReduceError[Item]

object SqsReduce {
  def apply(
    config: SqsReduceConfiguration,
    client: SqsAsyncClient)(implicit ec: ExecutionContext): SqsReduce =
  new SqsReduce(
    config = config,
    reEnqueueMessages = message =>
      SqsEnqueue(client)
        .forQueue(config.queue.url)
        .single(message)(implicitly[ToMessage[Message]]),
    deleteMessages = SqsDelete(client).forQueue(config.queue.url).batched[Message]
  )
}

/** SqsReduce combines several messages with the same Key into a single message
 *
 *  The combined message is enqueued and the original messages are deleted.
 */
class SqsReduce(
    config: SqsReduceConfiguration,
    reEnqueueMessages: Message => Future[SendMessageResponse],
    deleteMessages: List[Message] => Future[BatchResult[Message]]
  )(implicit ec: ExecutionContext) {

  /** Reduces batches of messages
   *
   *  If multiple messages belong together as a single update this can combine them into a single one and renqueue.
   *
   *  @tparam Item Input type that contains a message and provides a key to track deletes.
   *               Must provide a semigroup instance for Item type so that items can be combined.
   *               Must provide a ToMessage instance so the reduced items can be enqueued in a new `Message`.
   *               Keyed provides a method of extracting a key to group messages by. This is usually done with the
   *               the message's MessageGroupId and can be imported from [[com.nike.fleam.sqs.implicits]]
   *  @tparam GroupingKey Type of GroupingKey used for grouping messages and must be orderable. The default common
   *                      pattern of using MessageGroupId can be imported from [[com.nike.fleam.sqs.implicits.]]
   *  @return Flow from Item to either SqsReduceError or Items that had no duplicates
   */
  def flow[Item : Semigroup : ContainsMessage : ToMessage : Keyed[*, Either[MessageError, GroupingKey]], GroupingKey : Order]:
      Flow[Item, Either[SqsReduceError[Item], Item], org.apache.pekko.NotUsed] = {
    config.grouping.toFlow[Item]
      .flatMapConcat { batch =>

        val (noKeys, keyed) = batch
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

        val errors = noKeys.map { case (error, item) => NonEmptyList.of(MissingKey(error, item)).asLeft }

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
                .recover[Either[NonEmptyList[SqsReduceError[Item]], SendMessageResponse]] {
                  case ex: SqsException => items.map(EnqueueError(ex, _, reducedMessage)).asLeft[SendMessageResponse]
                }
            }
            batchResult <- EitherT.right[NonEmptyList[SqsReduceError[Item]]](deleteMessages(items.map(_.getMessage).toList))
          } yield {
            val count = items.length
            items.map[SqsReduceError[Item]] { item =>
              batchResult.failed.find(_.entry.id == item.getMessage.messageId)
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
