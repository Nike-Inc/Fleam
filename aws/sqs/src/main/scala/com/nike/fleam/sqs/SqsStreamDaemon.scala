package com.nike.fleam
package sqs

import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import configuration.SqsQueueProcessingConfiguration
import com.amazonaws.regions.Regions
import com.amazonaws.services.sqs.{AmazonSQSAsync, AmazonSQSAsyncClientBuilder}
import com.amazonaws.services.sqs.model.Message
import instances.ContainsMessageInstances._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/


object SqsStreamDaemon {
  def apply(
      name: String,
      sqsConfig: SqsQueueProcessingConfiguration,
      pipeline: Flow[Message, Message, akka.NotUsed]
    )(implicit
      ec: ExecutionContext
    ): SimplifiedStreamDeamon[akka.Done] = apply(
    name = name,
    sqsConfig = sqsConfig,
    pipeline = pipeline,
    client = AmazonSQSAsyncClientBuilder.standard().withRegion(Regions.fromName(sqsConfig.region)).build()
  )

  def apply(
      name: String,
      sqsConfig: SqsQueueProcessingConfiguration,
      pipeline: Flow[Message, Message, akka.NotUsed],
      client: AmazonSQSAsync
    )(implicit
      ec: ExecutionContext
    ): SimplifiedStreamDeamon[akka.Done] = new SimplifiedStreamDeamon[akka.Done] {

    val daemon = new StreamDaemon(name)

    val source = SqsSource(client).forQueue(sqsConfig)
    val sink: Sink[Message, Future[akka.Done]] =
      SqsDelete(client).forQueue(sqsConfig.queue.url).toFlow[Message](sqsConfig.delete).toMat(Sink.ignore)(Keep.right)

    def start(implicit materializer: ActorMaterializer) =
      daemon.start[Message, Message, akka.NotUsed, akka.Done](source, pipeline, sink)

    def stop(): Future[Unit] = daemon.stop()
  }
}
