package com.nike.fleam
package logging

import org.apache.pekko.stream._
import org.apache.pekko.stream.scaladsl._
import scala.concurrent.Future

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

trait MetricsLogger[MetricWrapper] {
  type Client = MetricWrapper => Future[Unit]
  val client: Client

  def log[T](f: Graph[FlowShape[T, MetricWrapper], org.apache.pekko.NotUsed], filter: T => Boolean = (_: T) => true):
      Graph[FlowShape[T, T], org.apache.pekko.NotUsed] = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val broadcast = builder.add(Broadcast[T](2))
    val logMetric =
      Flow[T]
        .filter(filter)
        .via(f)
        .mapAsync(1)(client)

    broadcast.out(0) ~> logMetric ~> Sink.ignore

    FlowShape(broadcast.in, broadcast.out(1))
  }

  def logCount[T](implicit f: Counter[T, MetricWrapper]): Flow[T, T, org.apache.pekko.NotUsed] = logCount[T]()

  def logCount[T](filter: T => Boolean = (_: T) => true)(implicit f: Counter[T, MetricWrapper]): Flow[T, T, org.apache.pekko.NotUsed] =
    Flow.fromGraph(log(f.flow, filter))
}
