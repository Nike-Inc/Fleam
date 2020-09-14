package com.nike.fleam.ops

import akka.stream._
import akka.stream.scaladsl._
import scala.language.implicitConversions

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

trait BroadcastMergeFlowOps {
  implicit def broadcastMergeFlowOps[In, Out](flow: Graph[FlowShape[In, Out], akka.NotUsed]): BroadcastMergeFlow[In, Out] =
    new BroadcastMergeFlow(flow)
}

class BroadcastMergeFlow[In, Out](val originalFlow: Graph[FlowShape[In, Out], akka.NotUsed]) extends AnyVal {
  /** Processes element through all the flows passed in and joins the results into a stream.
   *  Each flow must have the same in and out types.
   *  Does not preserve order
   */
  def broadcastMerge[Out1](flows: Graph[FlowShape[Out, Out1], akka.NotUsed]*): Flow[In, Out1, akka.NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val length = flows.length
      val inFlow = builder.add(originalFlow)
      val broadcast = builder.add(Broadcast[Out](length))
      val merge = builder.add(Merge[Out1](length))

      inFlow ~> broadcast
      for {
        i <- 0 until length
      } yield {
        val flow = builder.add(flows(i))
        broadcast ~> flow ~> merge
      }

      FlowShape(inFlow.in, merge.out)
    })
}
