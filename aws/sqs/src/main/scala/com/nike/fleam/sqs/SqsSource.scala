package com.nike.fleam
package sqs

import com.nike.fleam.configuration._
import sqs.configuration._
import org.apache.pekko.stream.scaladsl._
import org.apache.pekko.stream.{KillSwitches, ThrottleMode, UniqueKillSwitch}
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model._
import com.nike.fawcett.sqs._
import scala.concurrent.Future

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

object SqsSource {

  type Fetch = ReceiveMessageRequest => Future[ReceiveMessageResponse]

  def apply(client: SqsAsyncClient, messageModifier: Message => Message = identity) = {
    new SqsSource(
      fetchMessages = wrapRequest[ReceiveMessageRequest, ReceiveMessageResponse](client.receiveMessage),
      messageModifier = messageModifier
    )
  }
}

class SqsSource(fetchMessages: SqsSource.Fetch, messageModifier: Message => Message = identity) {

  def forQueue(
    config: SqsQueueProcessingConfiguration
  ): Source[Message, UniqueKillSwitch] = forQueue(
    url = config.queue.url,
    batchSize = config.source.batchSize,
    parallelism = config.source.parallelism,
    config = config.source.throttle,
    attributeNames = config.attributeNames,
    messageAttributeNames = config.messageAttributeNames,
    waitTimeSeconds = config.waitTimeSeconds)

  def forQueue(
      url: String,
      batchSize: Int = Default.Sqs.sourceConfig.batchSize,
      parallelism: Int = Default.Sqs.sourceConfig.parallelism,
      config: Option[ThrottleConfiguration] = Default.Sqs.sourceConfig.throttle,
      attributeNames: Set[String] = Set("All"),
      messageAttributeNames: Set[String] = Set(Default.Sqs.MessageAttributes.All),
      waitTimeSeconds: Int = 0): Source[Message, UniqueKillSwitch] = {

    val request = ReceiveMessageRequest.builder()
      .queueUrl(url)
      .maxNumberOfMessages(batchSize)
      // This should use types once this is fixed, see https://github.com/aws/aws-sdk-java-v2/issues/1892
      .messageSystemAttributeNamesWithStrings(attributeNames.toList :_*)
      .messageAttributeNames(messageAttributeNames.toList :_*)
      .waitTimeSeconds(waitTimeSeconds)
      .build()

    val throttling = config.map { c =>
      Flow[SqsSource.Fetch]
        .throttle(elements = c.elements, per = c.per, maximumBurst = c.maximumBurst, ThrottleMode.Shaping)
    } getOrElse Flow[SqsSource.Fetch]

    Source.repeat(fetchMessages)
      .viaMat(KillSwitches.single)(Keep.right)
      .via(throttling)
      .mapAsync(parallelism)(_(request))
      .map(ReceiveMessageResponseLens.messages.get)
      .mapConcat(identity)
      .map(messageModifier)
  }
}
