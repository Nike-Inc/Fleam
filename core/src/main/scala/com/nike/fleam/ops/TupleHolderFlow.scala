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

trait TupleHelperFlowOps {
  implicit def tupleHelperFlowOps[In, T, U, Mat](flow: Graph[FlowShape[In, (T, U)], Mat]): TupleHelperFlow[In, T, U, Mat] =
    new TupleHelperFlow(flow)
}

object TupleHelperFlowOps extends TupleHelperFlowOps

class TupleHelperFlow[In, T, U, Mat](val flow: Graph[FlowShape[In, (T, U)], Mat]) extends AnyVal {
  def mapRight[V](f: (U) => V): Flow[In, (T, V), Mat] = Flow.fromGraph(flow).map { case (t, u) => t -> f(u) }
  def mapLeft[V](f: (T) => V): Flow[In, (V, U), Mat] = Flow.fromGraph(flow).map { case (t, u) => f(t) -> u }
}
