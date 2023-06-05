package com.nike.fleam
package ops

import cats.implicits._
import akka.stream._
import akka.stream.scaladsl._
import scala.collection.immutable.Iterable
import scala.language.implicitConversions

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

trait EitherFlattenIterableOps {

  implicit def eitherFlattenIterableFlowOps[A, L, R, Mat](flow: Graph[FlowShape[A, Either[L, Iterable[R]]], Mat]):
    EitherFlattenIterable[Flow[A, *, Mat], L, R] =
      new EitherFlattenIterable[Flow[A, *, Mat], L, R](Flow.fromGraph(flow))

  implicit def eitherFlattenIterableSourceOps[L, R, Mat](source: Graph[SourceShape[Either[L, Iterable[R]]], Mat]):
    EitherFlattenIterable[Source[*, Mat], L, R] =
      new EitherFlattenIterable[Source[*, Mat], L, R](Source.fromGraph(source))
}

object EitherFlattenIterableOps extends EitherFlattenIterableOps

class EitherFlattenIterable[S[_], L, R](val stream: S[Either[L, Iterable[R]]]) extends AnyVal {
  /** Converts a `Either[L, Iterable[R]]` into individual `Either[L, R]`. Will not continue for inifinite sized mutable iterables.  */
  def flatten(implicit es: EitherStream[S, L, Iterable[R]]): S[Either[L, R]] = es.flatMapConcat(stream) {
    case Left(l) => Source.single(l.asLeft[R])
    case Right(rs: Iterable[R]) => Source(rs).map(Right(_))
    case Right(rs) => Source(rs).map(Right(_))
  }
}
