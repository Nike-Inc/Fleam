package com.nike.fleam.ops

import org.apache.pekko.stream._
import org.apache.pekko.stream.scaladsl._
import scala.concurrent.duration._
import scala.language.implicitConversions

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

trait TickingGroupedWithinFlowOps {
  implicit def tickingGroupedWithinFlowOps[In, Out](flow: Graph[FlowShape[In, Out], org.apache.pekko.NotUsed]): TickingGroupedWithinFlow[In, Out] =
    new TickingGroupedWithinFlow(flow)
}

object TickingGroupedWithinFlowOps extends TickingGroupedWithinFlowOps


class TickingGroupedWithinFlow[In, Out](val flow: Graph[FlowShape[In, Out], org.apache.pekko.NotUsed]) extends AnyVal {
  /** A form of groupedWithin that emits empty Seqs even if no items have passed through within the allotted time. */
  def tickingGroupedWithin(batchSize: Int, within: FiniteDuration): Flow[In, Seq[Out], org.apache.pekko.NotUsed] = Flow.fromGraph {
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val tickSource = builder.add(
        Source.tick(
          initialDelay = 0.seconds,
          interval = within,
          tick = Seq.empty[Out]
        )
        .buffer(size = 1, OverflowStrategy.backpressure)
      )
      val merge = builder.add(MergePreferred[Seq[Out]](secondaryPorts = 1, eagerComplete = true))
      val inFlow = builder.add(
        Flow.fromGraph(flow)
          .groupedWithin(batchSize, within)
      )

      inFlow ~> merge.preferred
      tickSource ~> merge.in(0)

      FlowShape(inFlow.in, merge.out)
    }
  }
}

