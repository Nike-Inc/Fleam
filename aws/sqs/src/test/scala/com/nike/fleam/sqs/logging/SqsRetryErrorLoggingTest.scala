package com.nike.fleam.sqs
package logging

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.Show
import cats.implicits._
import software.amazon.awssdk.services.sqs.model.Message
import scala.concurrent.duration._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class SqsRetryErrorLoggingTest extends AnyFlatSpec with Matchers {

  val showMessage = Show.show[Message] { message =>
    s"message: {messageId='${message.messageId}'}"
  }

  implicit val errorShow = new SqsRetryErrorLogging[Int](showMessage = showMessage).sqsRetryErrorShow

  val message = Message.builder.messageId("message-1").build()

  it should "log a ReEnqueued message" in {
    (ReEnqueued(message, 1): SqsRetryError[Int]).show shouldBe
    "Re-enqueued message. message: {messageId='message-1'}"
  }

  it should "log a CountError message" in {
    (CountError(message, 1, NumberFormatError("number-1", message)): SqsRetryError[Int]).show shouldBe
    "Failed to retrieve a valid retry count from message. Unable to parse retry value as integer. Tried parsing 'number-1' message: {messageId='message-1'}"
  }

  it should "log a DeadLettered message" in {
    (DeadLettered(message, 1): SqsRetryError[Int]).show shouldBe
    "Message was dead lettered. message: {messageId='message-1'}"
  }

  it should "log a ExceededRetriesDeadLettered message" in {
    (ExceededRetriesDeadLettered(message, 1, 10): SqsRetryError[Int]).show shouldBe
    "Exceeded the number of allowed retries (10) and dead lettered the message. message: {messageId='message-1'}"
  }

  it should "log a RetryEnqueueError message" in {
    (RetryEnqueueError(
      message,
      1,
      OpFailure(message, TooManyMessagesInBatch(11).asRight)): SqsRetryError[Int]).show shouldBe
    "Failed to re-enqueue message. Sqs operation failed. Failed to enqueue messsages. Too many message in batch. count=11 message: {messageId='message-1'}"
  }

  it should "log a ReEnqueuedNotDeletedError message" in {
    (ReEnqueuedNotDeletedError(
      message,
      1,
      OpFailure(message, EntryError("a-failure-1", "something AWS did failed.").asLeft)): SqsRetryError[Int]).show shouldBe
    "Tried to delete re-enqueued message but failed. Sqs operation failed. code=a-failure-1 reason=something AWS did failed. message: {messageId='message-1'}"
  }

  it should "log a ExceededRetriesNotDeletedError message" in {
    (ExceededRetriesNotDeletedError(
      message,
      1,
      OpFailure(message, EntryError("a-failure-1", "something AWS did failed.").asLeft)): SqsRetryError[Int]).show shouldBe
    "Tried to delete message after exceeding retry but failed. Sqs operation failed. code=a-failure-1 reason=something AWS did failed. message: {messageId='message-1'}"
  }

  it should "log a RetryDlqError message" in {
    (RetryDlqError(
      message,
      1,
      OpFailure(message, EntryError("a-failure-1", "something AWS did failed.").asLeft)): SqsRetryError[Int]).show shouldBe
    "Tried to dead letter message but failed. Sqs operation failed. code=a-failure-1 reason=something AWS did failed. message: {messageId='message-1'}"
  }

  it should "log a DlqedNotDeletedError message" in {
    (DlqedNotDeletedError(
      message,
      1,
      OpFailure(message, EntryError("a-failure-1", "something AWS did failed.").asLeft)): SqsRetryError[Int]).show shouldBe
    "Tried to delete message that was dead lettered but failed. Sqs operation failed. code=a-failure-1 reason=something AWS did failed. message: {messageId='message-1'}"
  }

  it should "log a MessageProcessingTimedOut message" in {
    (MessageProcessingTimedOut(message, 1, 2.minutes, 1.minutes): SqsRetryError[Int]).show shouldBe
    "Processing time elapsed before message could be dealt with. elapsed=120000 timeout=60000 message: {messageId='message-1'}"
  }

  it should "log a NoAwsResponse message" in {
    (NoAwsResponse(message, 1): SqsRetryError[Int]).show shouldBe
    "Didn't find a response for this message in the batch results message-1. message: {messageId='message-1'}"
  }

}
