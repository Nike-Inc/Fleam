package com.nike.fleam
package sqs
package instances

import com.amazonaws.services.sqs.model.Message
import cats.{Order, Show}
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
case class MissingGroupingKey(message: Message) extends MessageError

trait MessageInstances {
  implicit val messageGroupIdKeyed = Keyed.lift[Message, Either[MissingGroupingKey, MessageGroupId]] { message =>
    Either.fromOption(
      (MessageLens.attributes composeLens at(Attributes.MessageGroupId) get(message)).map(MessageGroupId),
      MissingGroupingKey(message))
  }

  implicit val messageGroupIdOrdering = Order.by[MessageGroupId, String](_.value)

  implicit val showMessageId: Show[MessageId] = Show.show(_.value)

  implicit val messageToMessage = ToMessage.lift[Message](identity)
}

object MessageInstances extends MessageInstances
