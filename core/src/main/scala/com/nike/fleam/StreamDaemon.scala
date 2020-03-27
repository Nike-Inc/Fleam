package com.nike.fleam

import org.slf4j.LoggerFactory
import akka.stream._
import akka.stream.scaladsl._
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

  def addKillSwitch[Out, Mat](source: Source[Out, Mat]): Source[Out, UniqueKillSwitch] =
    source.viaMat(KillSwitches.single)(Keep.right)
}

class StreamDaemon(name: String)(implicit ec: ExecutionContext) {
  import StreamDaemon._

  private val killSwitchPromise = Promise[KillSwitch]()
  val killSwitch = killSwitchPromise.future

  def start[SourceOut, FlowOut, Mat, SinkOut](
      source: Source[SourceOut, Mat],
      pipeline: Flow[SourceOut, FlowOut, Mat],
      sink: Sink[FlowOut, Future[SinkOut]]
    )(implicit materializer: ActorMaterializer): Future[SinkOut] = {

    logger.info(s"starting $name stream...")
    val graph: RunnableGraph[(UniqueKillSwitch, Future[SinkOut])] = addKillSwitch(source)
      .via(pipeline)
      .toMat(sink)(Keep.both)
    val (ks, out) = graph.run()
    killSwitchPromise.success(ks)
    out
  }

  def stop(): Future[Unit] = {
    logger.info(s"stopping $name string‥.")
    killSwitch.map(_.shutdown())
  }
}
