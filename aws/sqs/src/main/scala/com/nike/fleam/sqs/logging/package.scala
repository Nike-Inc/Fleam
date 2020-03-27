package com.nike.fleam

package sqs
import cats.Show
import cats.implicits._
import com.amazonaws.services.sqs.model.Message

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

package logging {

  class MessageCountErrorLogging(implicit showMessage: Show[Message] = logging.noShow[Message]) {
    val messageCountErrorShow = Show.show[MessageCountError] {
      case numberFormatError: NumberFormatError =>
        join(
          show"Unable to parse retry value as integer.",
          show"Tried parsing ${numberFormatError.attributeValue}",
          numberFormatError.message.show
        )
      case nullValueError: NullValueError =>
        join(
          show"Retry value not a string value. ${nullValueError.attributeValue.toString}",
          nullValueError.message.show
        )
      }
  }

  class SqsRetryErrorLogging[T](
    showT: Show[T] = logging.noShow[T],
    showMessage: Show[Message] = logging.noShow[Message],
    showMessageCountError: Show[MessageCountError] = new MessageCountErrorLogging().messageCountErrorShow) {

    implicit private val tShow = showT
    implicit private val messageShow = showMessage
    implicit private val messageCountErrorShow = showMessageCountError

    implicit val sqsRetryErrorShow = Show.show[SqsRetryError[T]] {
      case countError: CountError[T] =>
        join(
          show"Failed to retrieve a valid retry count from message.",
          countError.error.show
        )
      case reEnqueued: ReEnqueued[T] =>
        join(
          show"Re-enqueued message.",
          reEnqueued.in.show,
          reEnqueued.message.show
        )
      case deadLettered: DeadLettered[T] =>
        join(
          show"Message was dead lettered.",
          deadLettered.in.show,
          deadLettered.message.show
        )
      case exceeded: ExceededRetriesDeadLettered[T] =>
        join(
          show"Exceeded the number of allowed retries (${exceeded.maxRetries}) and dead lettered the message.",
          exceeded.in.show,
          exceeded.message.show
        )
      case retry: RetryEnqueueError[T] =>
          join(
            show"Failed to re-enqueue message. ${retry.error}",
            retry.in.show,
            retry.message.show
          )
      case notDeleted: ReEnqueuedNotDeletedError[T] =>
        join(
          show"Tried to delete re-enqueued message but failed. ${notDeleted.error}",
          notDeleted.in.show,
          notDeleted.message.show
        )
      case notDeleted: ExceededRetriesNotDeletedError[T] =>
        join(
          show"Tried to delete message after exceeding retry but failed. ${notDeleted.error}",
          notDeleted.in.show,
          notDeleted.message.show
        )
      case retry: RetryDlqError[T] =>
        join(
          show"Tried to dead letter message but failed. ${retry.error}",
          retry.in.show,
          retry.message.show
        )
      case notDeleted: DlqedNotDeletedError[T] =>
        join(
          show"Tried to delete message that was dead lettered but failed. ${notDeleted.error}",
          notDeleted.in.show,
          notDeleted.message.show
        )
      case timedOut: MessageProcessingTimedOut[T] =>
        join(
          show"Processing time elapsed before message could be dealt with.",
          timedOut.in.show,
          timedOut.message.show
        )
      case noResponse: NoAwsResponse[T] =>
        join(
          show"Didn't find a response for this message in the batch results ${noResponse.message.getMessageId}.",
          noResponse.in.show,
          noResponse.message.show
        )
      }
  }
}

package object logging {
  def noShow[T] = Show.show[T] { _ => "" }
  val showMessageId = Show.show[Message] { message =>
    show"messageId=${message.getMessageId}"
  }
  val showMessageToString = Show.show[Message] { message =>
    show"${message.toString}"
  }

  implicit val opErrorShow = Show.show[OpError] {
    case Right(error) => Show[SqsEnqueueError].show(error)
    case Left(error) => Show[EntryError].show(error)
  }

  implicit val opFailureShow: Show[OpFailure] = Show.show[OpFailure] { failure =>
    show"Sqs operation failed. ${failure.error}"
  }

  implicit val entryErrorShow: Show[EntryError] = Show.show[EntryError] { error =>
    s"code=${error.code} reason=${error.reason}"
  }

  implicit val sqsEnqueueErrorShow: Show[SqsEnqueueError] = Show.show[SqsEnqueueError] {
    case TooManyMessagesInBatch(count) => s"Failed to enqueue messsages. Too many message in batch. count=$count"
  }

  private[logging] def join(strings: String*): String = strings.filterNot(_.isEmpty).mkString(" ")
}
