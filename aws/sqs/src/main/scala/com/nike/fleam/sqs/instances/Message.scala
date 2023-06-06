package com.nike.fleam
package sqs
package instances

import software.amazon.awssdk.services.sqs.model.{Message, MessageSystemAttributeName}
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
  implicit val messageGroupIdKeyed: Keyed[Message, Either[MissingGroupingKey, MessageGroupId]] =
    Keyed.lift[Message, Either[MissingGroupingKey, MessageGroupId]] { message =>
      Either.fromOption(
        (MessageLens.attributes composeLens at(MessageSystemAttributeName.MESSAGE_GROUP_ID) get(message)).map(MessageGroupId),
        MissingGroupingKey(message))
    }

  implicit val messageGroupIdOrdering: Order[MessageGroupId] = Order.by[MessageGroupId, String](_.value)

  implicit val showMessageId: Show[MessageId] = Show.show(_.value)

  implicit val messageToMessage: ToMessage[Message] = ToMessage.lift[Message](identity)
}

object MessageInstances extends MessageInstances
