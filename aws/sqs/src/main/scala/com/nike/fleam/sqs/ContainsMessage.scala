package com.nike.fleam.sqs

import software.amazon.awssdk.services.sqs.model.Message
import scala.language.implicitConversions
import simulacrum._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

@typeclass trait ContainsMessage[-T] {
  def getMessage(t: T): Message
}

object ContainsMessage {
  def lift[T](f: T => Message) = new ContainsMessage[T] {
    def getMessage(t: T) = f(t)
  }

  implicit def eitherContainsMessage[L: ContainsMessage, R: ContainsMessage] = lift[Either[L, R]] {
    _.fold(ContainsMessage[L].getMessage, ContainsMessage[R].getMessage)
  }

}
