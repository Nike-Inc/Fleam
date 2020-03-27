package com.nike.fleam
package ops

import cats.implicits._
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

  implicit def eitherFlattenIterableFlowOps[A, L, R, Mat](flow: Flow[A, Either[L, Iterable[R]], Mat]):
    EitherFlattenIterable[Flow[A, ?, ?], L, R, Mat] =
      new EitherFlattenIterable[Flow[A, ?, ?], L, R, Mat](flow)

  implicit def eitherFlattenIterableSourceOps[L, R, Mat](source: Source[Either[L, Iterable[R]], Mat]):
    EitherFlattenIterable[Source, L, R, Mat] =
      new EitherFlattenIterable[Source, L, R, Mat](source)
}

object EitherFlattenIterableOps extends EitherFlattenIterableOps

class EitherFlattenIterable[S[_, _], L, R, Mat](val stream: S[Either[L, Iterable[R]], Mat]) extends AnyVal {
  /** Converts a `Either[L, Iterable[R]]` into individual `Either[L, R]`. Will not continue for inifinite sized mutable iterables.  */
  def flatten(implicit es: EitherStream[S, L, Iterable[R], Mat]): S[Either[L, R], Mat] = es.flatMapConcat(stream) {
    case Left(l) => Source.single(l.asLeft[R])
    case Right(rs: Iterable[R]) => Source(rs).map(Right(_))
    case Right(rs) => Source(rs).map(Right(_))
  }
}
