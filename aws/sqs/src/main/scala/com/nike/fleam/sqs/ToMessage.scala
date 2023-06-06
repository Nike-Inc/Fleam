package com.nike.fleam.sqs

import software.amazon.awssdk.services.sqs.model.Message
import simulacrum._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

@typeclass trait ToMessage[-T] {
  def toMessage(t: T): Message
}

object ToMessage {
  def lift[T](f: T => Message) = new ToMessage[T] {
    def toMessage(t: T) = f(t)
  }

  implicit def eitherToMessage[L: ToMessage, R: ToMessage]: ToMessage[Either[L, R]] = lift[Either[L, R]](
    _.fold(ToMessage[L].toMessage, ToMessage[R].toMessage)
  )
}
