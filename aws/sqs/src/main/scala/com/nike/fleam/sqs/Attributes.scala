package com.nike.fleam
package sqs

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

object Attributes {
  // https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html
  val MessageDeduplicationId = "MessageDeduplicationId"
  val MessageGroupId = "MessageGroupId"
}
