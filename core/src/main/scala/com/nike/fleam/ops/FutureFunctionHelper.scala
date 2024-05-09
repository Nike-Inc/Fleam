package com.nike.fleam
package ops

import org.apache.pekko.stream._
import org.apache.pekko.stream.scaladsl._
import scala.concurrent.Future
import scala.collection.immutable.Iterable
import scala.language.implicitConversions

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

trait FutureFunctionHelperOps extends FutureFunctionHelperOps2to22 {
  implicit def futureFunction1HelperOps[T, U](f: Function1[T, Future[U]]): FutureFunction1Helper[T, U] =
    new FutureFunction1Helper(f)
}

object FutureFunctionHelperOps extends FutureFunctionHelperOps

class FutureFunction1Helper[-T, +U](val f: Function1[T, Future[U]]) extends AnyVal {
  /** lifts a function from T => Future[U] into an ordered Flow */
  def toFlow(parallelism: Int): Flow[T, U, org.apache.pekko.NotUsed] =
    Flow[T]
      .mapAsync(parallelism)(f)

  /** lifts a function from T => Future[U] into an unordered Flow */
  def toFlowUnordered(parallelism: Int): Flow[T, U, org.apache.pekko.NotUsed] =
    Flow[T]
      .mapAsyncUnordered(parallelism)(f)

  def toSource(parallelism: Int)(in: Iterable[T]): Source[U, org.apache.pekko.NotUsed] =
    Source(in)
      .mapAsync(parallelism)(f)

  def toSourceUnordered(parallelism: Int)(in: Iterable[T]): Source[U, org.apache.pekko.NotUsed] =
    Source(in)
      .mapAsyncUnordered(parallelism)(f)

  /** Shorthand for running a source through an async function with parallelism */
  def toStream(parallelism: Int)(in: Iterable[T])(implicit mat: Materializer): Future[Seq[U]] =
    toSource(parallelism)(in).runWith(Sink.seq)

  /** Shorthand for running a source through an async function with parallelism unordered */
  def toStreamUnordered(parallelism: Int)(in: Iterable[T])(implicit mat: Materializer): Future[Seq[U]] =
    toSourceUnordered(parallelism)(in).runWith(Sink.seq)
}
