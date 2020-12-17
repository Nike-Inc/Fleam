package com.nike.fleam

package sqs
import cats.Show
import cats.implicits._
import software.amazon.awssdk.services.sqs.model.Message

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

package object logging {
  def noShow[T] = Show.show[T] { _ => "" }
  val showMessageId = Show.show[Message] { message =>
    show"messageId=${message.messageId}"
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
