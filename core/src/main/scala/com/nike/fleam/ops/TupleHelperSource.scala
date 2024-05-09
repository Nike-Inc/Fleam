package com.nike.fleam.ops

import org.apache.pekko.stream._
import org.apache.pekko.stream.scaladsl._
import scala.language.implicitConversions

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

trait TupleHelperSourceOps {
  implicit def tupleHelperSourceOps[T, U, Mat](source: Graph[SourceShape[(T, U)], Mat]): TupleHelperSource[T, U, Mat] =
    new TupleHelperSource(source)
}

class TupleHelperSource[T, U, Mat](val source: Graph[SourceShape[(T, U)], Mat]) extends AnyVal {
  def mapRight[V](f: (U) => V): Source[(T, V), Mat] = Source.fromGraph(source).map { case (t, u) => t -> f(u) }
  def mapLeft[V](f: (T) => V): Source[(V, U), Mat] = Source.fromGraph(source).map { case (t, u) => f(t) -> u }
}
