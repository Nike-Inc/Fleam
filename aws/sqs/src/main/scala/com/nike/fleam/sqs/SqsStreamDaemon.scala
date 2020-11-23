package com.nike.fleam
package sqs

import akka.stream.{Materializer, FlowShape, Graph, UniqueKillSwitch}
import akka.stream.scaladsl._
import configuration.SqsQueueProcessingConfiguration
import com.amazonaws.regions.Regions
import com.amazonaws.services.sqs.{AmazonSQSAsync, AmazonSQSAsyncClientBuilder}
import com.amazonaws.services.sqs.model.Message
import instances.ContainsMessageInstances._
import instances.MessageInstances._

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
      pipeline: Graph[FlowShape[Message, Message], akka.NotUsed],
      batchDeleteResults: Graph[FlowShape[BatchResult[Message], BatchResult[Message]], akka.NotUsed] =
        Flow[BatchResult[Message]]
    )(implicit
      ec: ExecutionContext
    ): SimplifiedStreamDeamon[akka.Done] = apply(
    name = name,
    sqsConfig = sqsConfig,
    pipeline = pipeline,
    client = AmazonSQSAsyncClientBuilder.standard().withRegion(Regions.fromName(sqsConfig.region)).build(),
    batchDeleteResults
  )

  def apply[Mat](
      name: String,
      sqsConfig: SqsQueueProcessingConfiguration,
      pipeline: Graph[FlowShape[Message, Message], Mat],
      client: AmazonSQSAsync,
      batchDeleteResults: Graph[FlowShape[BatchResult[Message], BatchResult[Message]], akka.NotUsed]
    )(implicit
      ec: ExecutionContext
    ): SimplifiedStreamDeamon[akka.Done] = new SimplifiedStreamDeamon[akka.Done] {

    val daemon = new StreamDaemon(name)

    val source = SqsSource(client).forQueue(sqsConfig)
    val sink: Sink[Message, Future[akka.Done]] =
      SqsDelete(client).forQueue(sqsConfig.queue.url)
        .toFlow[Message, MessageId](sqsConfig.delete)
        .via(batchDeleteResults)
        .toMat(Sink.ignore)(Keep.right)

    def start(implicit materializer: Materializer) =
      daemon.start[Message, Message, UniqueKillSwitch, Mat, akka.Done](source, pipeline, sink)

    def stop(): Future[Unit] = daemon.stop()
  }
}
