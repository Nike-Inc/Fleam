package com.nike.fleam
package sqs

import org.apache.pekko.stream.{Materializer, FlowShape, Graph, Supervision, UniqueKillSwitch}
import org.apache.pekko.stream.scaladsl._
import configuration.SqsQueueProcessingConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.Message
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
      pipeline: Graph[FlowShape[Message, Message], org.apache.pekko.NotUsed],
      batchDeleteResults: Graph[FlowShape[BatchResult[Message], BatchResult[Message]], org.apache.pekko.NotUsed] =
        Flow[BatchResult[Message]]
    )(implicit
      ec: ExecutionContext
    ): SimplifiedStreamDeamon[org.apache.pekko.Done] = apply(
    name = name,
    sqsConfig = sqsConfig,
    pipeline = pipeline,
    client = SqsAsyncClient.builder().region(Region.of(sqsConfig.region)).build(),
    batchDeleteResults
  )

  def apply[Mat](
      name: String,
      sqsConfig: SqsQueueProcessingConfiguration,
      pipeline: Graph[FlowShape[Message, Message], Mat],
      client: SqsAsyncClient,
      batchDeleteResults: Graph[FlowShape[BatchResult[Message], BatchResult[Message]], org.apache.pekko.NotUsed]
    )(implicit
      ec: ExecutionContext
    ): SimplifiedStreamDeamon[org.apache.pekko.Done] = new SimplifiedStreamDeamon[org.apache.pekko.Done] {

    val daemon = new StreamDaemon(name)

    val source = SqsSource(client).forQueue(sqsConfig)
    val sink: Sink[Message, Future[org.apache.pekko.Done]] =
      SqsDelete(client).forQueue(sqsConfig.queue.url)
        .toFlow[Message](sqsConfig.delete)
        .via(batchDeleteResults)
        .toMat(Sink.ignore)(Keep.right)


    def start(implicit materializer: Materializer) =
      daemon.start[Message, Message, UniqueKillSwitch, Mat, org.apache.pekko.Done](source, pipeline, sink)

    def start(supervisionStrategy: Supervision.Decider)(implicit materializer: Materializer) =
      daemon.start[Message, Message, UniqueKillSwitch, Mat, org.apache.pekko.Done](source, pipeline, sink, supervisionStrategy)


    def stop(): Future[Unit] = daemon.stop()
  }
}
