package com.nike.fleam

import org.slf4j.LoggerFactory
import org.apache.pekko.stream._
import org.apache.pekko.stream.scaladsl._
import concurrent.{ Future, Promise }
import scala.concurrent.ExecutionContext

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

object StreamDaemon {
  val logger = LoggerFactory.getLogger(this.getClass)

  def addKillSwitch[Out, Mat](source: Graph[SourceShape[Out], Mat]): Source[Out, (Mat, UniqueKillSwitch)] =
    Source.fromGraph(source).viaMat(KillSwitches.single)(Keep.both)
}

class StreamDaemon(name: String)(implicit ec: ExecutionContext) {
  import StreamDaemon._

  private val killSwitchPromise = Promise[List[KillSwitch]]()
  val killSwitches: Future[List[KillSwitch]] = killSwitchPromise.future

  def start[SourceOut, FlowOut, SourceMat, PipelineMat, SinkOut](
      source: Graph[SourceShape[SourceOut], SourceMat],
      pipeline: Graph[FlowShape[SourceOut, FlowOut], PipelineMat],
      sink: Graph[SinkShape[FlowOut], Future[SinkOut]]
    )(implicit materializer: Materializer): Future[SinkOut] = {

    logger.info(s"starting $name stream...")
    val graph: RunnableGraph[(((SourceMat, UniqueKillSwitch), PipelineMat), Future[SinkOut])] = addKillSwitch(source)
      .viaMat(pipeline)(Keep.both)
      .toMat(sink)(Keep.both)
    val (((sourceMat, ks), pipelineMat), out) = graph.run()
    killSwitchPromise.success(List[Any](sourceMat, ks, pipelineMat).collect { case killSwitch: KillSwitch => killSwitch })
    out
  }

  def start[SourceOut, FlowOut, SourceMat, PipelineMat, SinkOut](
      source: Graph[SourceShape[SourceOut], SourceMat],
      pipeline: Graph[FlowShape[SourceOut, FlowOut], PipelineMat],
      sink: Graph[SinkShape[FlowOut], Future[SinkOut]],
      supervisionStrategy: Supervision.Decider
    )(implicit materializer: Materializer): Future[SinkOut] = {

    logger.info(s"starting $name stream...")
    val graph: RunnableGraph[(((SourceMat, UniqueKillSwitch), PipelineMat), Future[SinkOut])] = addKillSwitch(source)
      .viaMat(pipeline)(Keep.both)
      .toMat(sink)(Keep.both)
      .withAttributes(ActorAttributes.supervisionStrategy(supervisionStrategy))
    val (((sourceMat, ks), pipelineMat), out) = graph.run()
    killSwitchPromise.success(List[Any](sourceMat, ks, pipelineMat).collect { case killSwitch: KillSwitch => killSwitch })
    out
  }

  def stop(): Future[Unit] = {
    logger.info(s"stopping $name stream...")
    killSwitches.map(_.map(_.shutdown()))
  }
}
