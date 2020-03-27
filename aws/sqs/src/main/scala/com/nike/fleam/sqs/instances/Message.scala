package com.nike.fleam
package sqs
package instances

import com.amazonaws.services.sqs.model.Message
import cats.implicits._
import com.nike.fawcett.sqs._
import monocle.function.all._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

sealed trait MessageError
case class MissingMessageGroupId(message: Message) extends MessageError

trait MessageInstances {
  implicit val keyedMessage = Keyed.lift[Message, Either[MessageError, String]] { message =>
    Either.fromOption(MessageLens.attributes composeLens at("MessageGroupId") get(message), MissingMessageGroupId(message))
  }

  implicit val messageToMessage = ToMessage.lift[Message](identity)
}

object MessageInstances extends MessageInstances
