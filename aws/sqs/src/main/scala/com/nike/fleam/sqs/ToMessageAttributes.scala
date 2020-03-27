package com.nike.fleam.sqs

import com.amazonaws.services.sqs.model.MessageAttributeValue
import simulacrum._
import scala.language.implicitConversions

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

/** Convenience type class for creating a map of message attribute values to place into an sqs message */
@typeclass trait ToMessageAttributes[-T] {
  def toMessageAttributes(t: T): Map[String, MessageAttributeValue]
}

object ToMessageAttributes {
  def lift[T](f: T => Map[String, MessageAttributeValue]) = new ToMessageAttributes[T] {
    def toMessageAttributes(t: T): Map[String, MessageAttributeValue] = f(t)
  }

  implicit def eitherToMessageAttributes[L: ToMessageAttributes, R: ToMessageAttributes] = lift[Either[L, R]](
    _.fold(ToMessageAttributes[L].toMessageAttributes, ToMessageAttributes[R].toMessageAttributes)
  )
}
