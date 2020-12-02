package com.nike.fleam
package sqs

import scala.concurrent.duration._
import com.nike.fleam.configuration._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

package configuration {
  case class SqsQueueProcessingConfiguration(
    queue: SqsQueueConfiguration,
    source: SqsSourceConfiguration = Default.Sqs.sourceConfig,
    delete: SqsProcessingConfiguration = Default.Sqs.deleteConfig,
    attributeNames: Set[String] = Set("ALL"),
    messageAttributeNames: Set[String] = Set(Default.Sqs.MessageAttributes.All),
    region: String,
    waitTimeSeconds: Int = 20
  )

  case class SqsQueueConfiguration(url: String)
  case class SqsSourceConfiguration(batchSize: Int, parallelism: Int, throttle: Option[ThrottleConfiguration])
  case class SqsProcessingConfiguration(parallelism: Int, groupedWithin: GroupedWithinConfiguration)
  case class SqsRetryConfiguration(
    queue: SqsQueueConfiguration,
    deadLetterQueue: SqsQueueConfiguration,
    sqsProcessing: SqsProcessingConfiguration = Default.Sqs.enqueueConfig,
    maxRetries: Int = 10,
    timeout: Duration,
    retryCountKey: String = "retryCount"
  )

  case class SqsReduceConfiguration(
    queue: SqsQueueConfiguration,
    grouping: GroupedWithinConfiguration,
    deleteParallelism: Int)

  object Default {
    object Sqs {
      // https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html
      object MessageAttributes {
        val All = "All"
      }

      val sourceConfig = SqsSourceConfiguration(
        batchSize = 10,
        parallelism = 10,
        throttle = None)

      val deleteConfig = SqsProcessingConfiguration(
        parallelism = 10,
        groupedWithin = GroupedWithinConfiguration(
          batchSize = 10,
          within = 1.seconds)
      )

      val enqueueConfig = SqsProcessingConfiguration(
        parallelism = 10,
        groupedWithin = GroupedWithinConfiguration(
          batchSize = 10,
          within = 1.seconds)
      )
    }
  }
}
