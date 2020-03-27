package com.nike.fleam.ops

import akka.stream.scaladsl._
import cats._
import cats.implicits._
import scala.concurrent.{ExecutionContext, Future}
import stream._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

trait LiftsOps {
  /** Lifts a function from `T => U` into a flow that that takes an `M[T]` and only processes if the value of `M[U]` */
  def mFlow[M[_], T, U](f: T => U)(implicit monad: Monad[M]): Flow[M[T], M[U], akka.NotUsed] = {
    Flow[M[T]].mMap(f)
  }

  /** Lifts a function from `R => R1` into a flow that that takes an `Either[L, R]` and only processes if the value of `Either` is `R` */
  def eitherFlow[L, R, R1](f: R => R1): Flow[Either[L, R], Either[L, R1], akka.NotUsed] =
    mFlow[Either[L, ?], R, R1](f)

  /** Lifts a function from `T => Future[U]` into a flow that runs async from `M[T]` to `M[U]` */
  def mFlowAsync[M[_], T, U]
      (parallelism: Int)
      (f: T => Future[U])(implicit trav: Traverse[M], ec: ExecutionContext) =
    Flow[M[T]]
      .mMapAsync(parallelism)(f)

  /** Lifts a function from `R => Future[R1]` into a flow that runs async from `Either[L, R]` to `Either[L, R1]` */
  def eitherFlowAsync[L, R, R1](parallelism: Int)(f: R => Future[R1])(implicit ec: ExecutionContext) =
    mFlowAsync[Either[L, ?], R, R1](parallelism)(f)

  /** Lifts a function from `T => Future[U]` into a flow that runs async from `M[T]` to `M[U]`.
   *  Order is not preserved.
   */
  def mFlowAsyncUnordered[M[_], T, U]
      (parallelism: Int)
      (f: T => Future[U])(implicit trav: Traverse[M], ec: ExecutionContext)
      : Flow[M[T], M[U], akka.NotUsed] =
    Flow[M[T]]
      .mMapAsyncUnordered(parallelism)(f)

  /** Lifts a function from `R => Future[R1]` into a flow that runs async from `Either[L, R]` to `Either[L, R1]`.
   *  Order is not preserved.
   */
  def eitherFlowAsyncUnordered[L, R, R1](parallelism: Int)(f: R => Future[R1])(implicit ec: ExecutionContext) =
    mFlowAsyncUnordered[Either[L, ?], R, R1](parallelism)(f)
}

object Lifts extends LiftsOps
