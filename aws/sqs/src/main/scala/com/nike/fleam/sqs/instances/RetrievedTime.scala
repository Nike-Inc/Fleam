package com.nike.fleam.sqs
package instances

import ContainsRetrievedMessage.ops._
import RetrievedTime.ops._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

trait RetrievedTimeInstances {
  implicit val receivedMessageRetrievedTime: RetrievedTime[RetrievedMessage] = RetrievedTime.lift[RetrievedMessage](_.timestamp)

  implicit def retrievedTimeFromContainsRetrievedMessage[T: ContainsRetrievedMessage]: RetrievedTime[T] = RetrievedTime.lift[T] {
    _.getRetrievedMessage.getRetrievedTime
  }
}

object RetrievedTimeInstances extends RetrievedTimeInstances
