package com.nike.fleam.ops

import akka.stream._
import akka.stream.scaladsl._
import scala.concurrent.duration._
import scala.language.implicitConversions

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

trait TickingGroupedWithinSourceOps {
  implicit def tickingGroupedWithinSourceOps[Out](source: Graph[SourceShape[Out], akka.NotUsed]): TickingGroupedWithinSource[Out] =
    new TickingGroupedWithinSource(source)
}

object TickingGroupedWithinSourceOps extends TickingGroupedWithinSourceOps

class TickingGroupedWithinSource[Out](val source: Graph[SourceShape[Out], akka.NotUsed]) extends AnyVal {
  /** A form of groupedWithin that emits empty Seqs even if no items have passed through within the allotted time. */
  def tickingGroupedWithin(batchSize: Int, within: FiniteDuration): Source[Seq[Out], akka.NotUsed] = Source.fromGraph {
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
      val inSource = builder.add(
        Source.fromGraph(source)
          .groupedWithin(batchSize, within)
      )

      inSource ~> merge.preferred
      tickSource ~> merge.in(0)

      SourceShape(merge.out)
    }
  }
}
