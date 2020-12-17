package com.nike.fleam.sqs
package logging

import cats.Show
import cats.implicits._
import software.amazon.awssdk.services.sqs.model.Message

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
        countError.error.show,
        countError.message.show
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
        show"Processing time elapsed before message could be dealt with. elapsed=${timedOut.elapsed.toMillis} timeout=${timedOut.timeout.toMillis}",
        timedOut.in.show,
        timedOut.message.show
      )
    case noResponse: NoAwsResponse[T] =>
      join(
        show"Didn't find a response for this message in the batch results ${noResponse.message.messageId}.",
        noResponse.in.show,
        noResponse.message.show
      )
    }
}
