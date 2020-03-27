package com.nike.fleam.sqs

import simulacrum._
import scala.language.implicitConversions

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

/** Convenience type class for supplied [[com.nike.fleam.sqs.RetrievedMessage]] class.
 *
 *  Lets you automatically derive [[com.nike.fleam.sqs.ContainsMessage]] and [[com.nike.fleam.sqs.RetrievedTime]] instances.
 */
@typeclass trait ContainsRetrievedMessage[-T] {
  def getRetrievedMessage(t: T): RetrievedMessage
}

object ContainsRetrievedMessage {
  def lift[T](f: T => RetrievedMessage) = new ContainsRetrievedMessage[T] {
    def getRetrievedMessage(t: T): RetrievedMessage = f(t)
  }

  implicit def eitherContainsRetrievedMessage[L: ContainsRetrievedMessage, R: ContainsRetrievedMessage] =
    lift[Either[L, R]](_.fold(
      ContainsRetrievedMessage[L].getRetrievedMessage,
      ContainsRetrievedMessage[R].getRetrievedMessage))
}
