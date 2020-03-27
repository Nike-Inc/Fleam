package com.nike.fleam
package sqs

import com.amazonaws.services.sqs.model.{Message, MessageAttributeValue}
import cats.implicits._
import com.nike.fawcett.sqs._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

sealed trait MessageCountError
case class NumberFormatError(attributeValue: String, message: Message) extends MessageCountError
case class NullValueError(attributeValue: MessageAttributeValue, message: Message) extends MessageCountError

object MessageAttributes {

  // use a type alias here instead of kind-projector to help ops._ work
  type Count[T] = Either[MessageCountError, T]

  def count(key: String): ContainsCount[Message, Count] =
    new ContainsCount[Message, Count] {
      private def countAttribute(count: Int): MessageAttributeValue = new MessageAttributeValue()
        .withDataType("Number")
        .withStringValue(count.toString)

      def getCount(message: Message): Either[MessageCountError, Int] = {
        val attribute = MessageLens.messageAttributes.get(message).getOrElse(key, countAttribute(0))

        for {
          string <- Either.fromOption[MessageCountError, String](
            Option(attribute.getStringValue),
            NullValueError(attribute, message)
          )
          count <- Either.catchNonFatal(string.toInt)
            .leftMap(_ => NumberFormatError(string, message))
        } yield {
          count
        }
      }

      def setCount(message: Message)(count: Int): Message =
        MessageLens.messageAttributes.modify(_ + (key -> countAttribute(count)))(message)
    }
}
