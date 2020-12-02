package com.nike.fleam.sqs
package instances

import software.amazon.awssdk.services.sqs.model.Message
import ContainsRetrievedMessage.ops._
import ContainsMessage.ops._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

trait ContainsMessageInstances {
  implicit val retrievedContainsMessage = ContainsMessage.lift[RetrievedMessage](_.message)

  implicit val sqsRetryErrorContainsMessage = ContainsMessage.lift[SqsRetryError[_]](_.message)

  implicit def messageFromRetrievedContainsMessage[T: ContainsRetrievedMessage] = ContainsMessage.lift[T] {
    _.getRetrievedMessage.getMessage
  }

  implicit val messageContainsMessage = ContainsMessage.lift[Message](identity)
}

object ContainsMessageInstances extends ContainsMessageInstances
