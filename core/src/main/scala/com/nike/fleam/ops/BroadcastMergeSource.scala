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

trait BroadcastMergeSourceOps {
  implicit def broadcastMergeSourceOps[In](source: Source[In, akka.NotUsed]): BroadcastMergeSource[In] =
    new BroadcastMergeSource(source)
}

object BroadcastMergeSourceOps extends BroadcastMergeSourceOps

class BroadcastMergeSource[In](val source: Source[In, akka.NotUsed]) extends AnyVal {
  /** Processes element through all the flows passed in and joins the results into a stream.
   *  Each flow must have the same in and out types.
   *  Does not preserve order
   */
  def broadcastMerge[Out](flows: Flow[In, Out, akka.NotUsed]*): Source[Out, akka.NotUsed] =
    Source.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val length = flows.length
      val broadcast = builder.add(Broadcast[In](length))
      val merge = builder.add(Merge[Out](length))

      builder.add(source) ~> broadcast
      for {
        i <- 0 until length
      } yield {
        val flow = builder.add(flows(i))
        broadcast ~> flow ~> merge
      }

      SourceShape(merge.out)
    })
}
