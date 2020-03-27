package com.nike.fleam
package sqs

import com.nike.fleam.configuration._
import sqs.configuration._
import akka.stream.scaladsl._
import akka.stream.ThrottleMode
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model._
import com.nike.fawcett.sqs._
import scala.concurrent.Future

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

object SqsSource {

  type Fetch = ReceiveMessageRequest => Future[ReceiveMessageResult]

  def apply(client: AmazonSQSAsync, messageModifier: Message => Message = identity) = {
    new SqsSource(
      fetchMessages = wrapRequest[ReceiveMessageRequest, ReceiveMessageResult](client.receiveMessageAsync),
      messageModifier = messageModifier
    )
  }
}

class SqsSource(fetchMessages: SqsSource.Fetch, messageModifier: Message => Message = identity) {

  def forQueue(
    config: SqsQueueProcessingConfiguration
  ): Source[Message, akka.NotUsed] = forQueue(
    url = config.queue.url,
    batchSize = config.source.batchSize,
    parallelism = config.source.parallelism,
    config = config.source.throttle,
    attributeNames = config.attributeNames,
    waitTimeSeconds = config.waitTimeSeconds)

  def forQueue(
      url: String,
      batchSize: Int = Default.Sqs.sourceConfig.batchSize,
      parallelism: Int = Default.Sqs.sourceConfig.parallelism,
      config: Option[ThrottleConfiguration] = Default.Sqs.sourceConfig.throttle,
      attributeNames: Set[String] = Default.Sqs.attributeNames,
      waitTimeSeconds: Int = 0): Source[Message, akka.NotUsed] = {

    val request = new ReceiveMessageRequest()
      .withQueueUrl(url)
      .withMaxNumberOfMessages(batchSize)
      .withAttributeNames(attributeNames.toSeq:_*)
      .withMessageAttributeNames(Default.Sqs.Attributes.All)
      .withWaitTimeSeconds(waitTimeSeconds)

    val throttling = config.map { c =>
      Flow[SqsSource.Fetch]
        .throttle(elements = c.elements, per = c.per, maximumBurst = c.maximumBurst, ThrottleMode.Shaping)
    } getOrElse Flow[SqsSource.Fetch]

    Source.repeat(fetchMessages)
      .via(throttling)
      .mapAsync(parallelism)(_(request))
      .map(ReceiveMessageResultLens.messages.get)
      .mapConcat(identity)
      .map(messageModifier)
  }
}
