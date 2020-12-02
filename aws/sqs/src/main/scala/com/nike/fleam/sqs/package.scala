package com.nike.fleam

import java.time.Instant
import software.amazon.awssdk.services.sqs.model.Message

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

package sqs {
  case class EntryError(code: String, reason: String)

  case class RetrievedMessage(message: Message, timestamp: Instant)

  case class MessageId(value: String) extends AnyVal

  case class MessageGroupId(value: String) extends AnyVal
}

package object sqs extends FutureWrapper {

  type OpError = Either[EntryError, SqsEnqueueError]

}
