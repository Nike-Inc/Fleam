package com.nike.fleam
package ops

import akka.stream.{Graph, SourceShape}
import akka.stream.scaladsl._
import cats._
import cats.implicits._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

trait EitherStream[S[_, _], L, R, Mat] {

  def leftFixed: Stream[S, Either[L, ?], R, Mat]
  def unfixed: Stream[S, Id[?], Either[L, R], Mat]

  /** Takes a function from `R => R1` and only applies it if the incoming `Either[L, R]` is `R` */
  def eitherMap[R1](stream: S[Either[L, R], Mat])(f: R => R1): S[Either[L, R1], Mat] =
    leftFixed.mMap(stream)(f)

  /** Takes a function from `L => L1` and only applies it if the incoming `Either[L, R]` is `L` */
  def eitherLeftMap[L1](stream: S[Either[L, R], Mat])(f: L => L1): S[Either[L1, R], Mat] =
    unfixed.map(stream)(_.leftMap(f))

  /** Takes a function from `T => Either[L, R]` and lifts it into a flow that takes `Either[L, T]` and returns
   *  `Either[L, R]` if the value passed in is `T`
   */
  def eitherFlatMap[R1](stream: S[Either[L, R], Mat])(f: R => Either[L, R1]): S[Either[L, R1], Mat] =
    leftFixed.mFlatMap(stream)(f)

  /** Takes a function from `T => Future[R]` and lifts it into an async flow that takes `Either[L, T]` and returns
   *  `Either[L, R]` if the value passed in is `T`
   */
  def eitherMapAsync[R1]
      (stream: S[Either[L, R], Mat])
      (parallelism: Int)
      (f: R => Future[R1])
      (implicit ec: ExecutionContext): S[Either[L, R1], Mat] =
    leftFixed.mMapAsync(stream)(parallelism)(f)

  /** Takes a function from `T => Future[R]` and lifts it into an unordered async flow that takes `Either[L, T]` and returns
   *  `Either[L, R]` if the value passed in is `T`
   */
  def eitherMapAsyncUnordered[R1]
      (stream: S[Either[L, R], Mat])
      (parallelism: Int)
      (f: R => Future[R1])
      (implicit ec: ExecutionContext): S[Either[L, R1], Mat] =
    leftFixed.mMapAsyncUnordered(stream)(parallelism)(f)

  /** Takes a function from `T => Future[Either[L, R]]` and lifts it into an async flow that takes `Either[L, T]` and returns
   *  `Either[L, R]` if the value passed in is `T`
   */
  def eitherFlatMapAsync[R1]
      (stream: S[Either[L, R], Mat])
      (parallelism: Int)
      (f: R => Future[Either[L, R1]])
      (implicit ec: ExecutionContext): S[Either[L, R1], Mat] =
    leftFixed.mFlatMapAsync(stream)(parallelism)(f)

  /** Takes a function from `Either[L, R]` that creats a Source of eithers to be introduced into the stream. */
  def flatMapConcat[L1, R1](stream: S[Either[L, R], Mat])(f: Either[L, R] => Graph[SourceShape[Either[L1, R1]], akka.NotUsed]): S[Either[L1, R1], Mat] =
    unfixed.flatMapConcat(stream)(f)

  /** Takes a function from `T => Future[Either[L, R]]` and lifts it into an unordered async flow that takes `Either[L, T]` and returns
   *  `Either[L, R]` if the value passed in is `T`
   */
  def eitherFlatMapAsyncUnordered[R1]
      (stream: S[Either[L, R], Mat])
      (parallelism: Int)
      (f: R => Future[Either[L, R1]])
      (implicit ec: ExecutionContext): S[Either[L, R1], Mat] =
    leftFixed.mFlatMapAsyncUnordered(stream)(parallelism)(f)

  /** Join nested eithers to the right given the outer left-hand type is a super-type of the inner left-hand type */
  def joinRight[L1 >: L, R1]
      (stream: S[Either[L, R], Mat])
      (implicit ev: R <:< Either[L1, R1]) : S[Either[L1, R1], Mat] =
    unfixed.map(stream)(_.joinRight)

  /** Join nested eithers to the right given the outer left-hand type can be converted to a super-type of the inner left-hand type */
  def joinRight[L1, R1]
      (stream: S[Either[L, R], Mat], convert: L => L1)
      (implicit ev: R <:< Either[L1, R1]): S[Either[L1, R1], Mat] =
    unfixed.map(stream)(_.leftMap(convert).joinRight)

  /** Join nested eithers to the left given the outer right-hand type is a super-type of the inner right-hand type */
  def joinLeft[L1, R1 >: R]
      (stream: S[Either[L, R], Mat])
      (implicit ev: L <:< Either[L1, R1]) : S[Either[L1, R1], Mat] =
    unfixed.map(stream)(_.joinLeft)

  /** Join nested eithers to the left given the outer right-hand type can be converted to a super-type of the inner right-hand type */
  def joinLeft[L1, R1](stream: S[Either[L, R], Mat], convert: R => R1)(implicit ev: L <:< Either[L1, R1]): S[Either[L1, R1], Mat] =
    unfixed.map(stream)(_.map(convert).joinLeft)

  def dropRight(stream: S[Either[L, R], Mat]): S[L, Mat] =
    unfixed.collect[L](stream) { case Left(l) => l }

  def dropLeft(stream: S[Either[L, R], Mat]): S[R, Mat] =
    unfixed.collect[R](stream) { case Right(r) => r }
}

object EitherStream {

  def apply[S[_, _], L, R, Mat](implicit instance: EitherStream[S, L, R, Mat]) = instance

  trait Ops[S[_, _], L, R, Mat] {
    def typeClassInstance: EitherStream[S, L, R, Mat]
    def self: S[Either[L, R], Mat]

    def eitherMap[R1](f: R => R1): S[Either[L, R1], Mat] =
      typeClassInstance.eitherMap(self)(f)

    def eitherLeftMap[L1](f: L => L1): S[Either[L1, R], Mat] =
      typeClassInstance.eitherLeftMap(self)(f)

    def eitherFlatMap[R1](f: R => Either[L, R1]): S[Either[L, R1], Mat] =
      typeClassInstance.eitherFlatMap(self)(f)

    def eitherMapAsync[R1]
        (parallelism: Int)
        (f: R => Future[R1])
        (implicit ec: ExecutionContext): S[Either[L, R1], Mat] =
      typeClassInstance.eitherMapAsync(self)(parallelism)(f)(ec)

    def eitherMapAsyncUnordered[R1]
        (parallelism: Int)
        (f: R => Future[R1])
        (implicit ec: ExecutionContext): S[Either[L, R1], Mat] =
      typeClassInstance.eitherMapAsyncUnordered(self)(parallelism)(f)(ec)

    def eitherFlatMapAsync[R1]
        (parallelism: Int)
        (f: R => Future[Either[L, R1]])
        (implicit ec: ExecutionContext): S[Either[L, R1], Mat] =
      typeClassInstance.eitherFlatMapAsync(self)(parallelism)(f)(ec)

    def flatMapConcat[L1, R1](f: Either[L, R] => Graph[SourceShape[Either[L1, R1]], akka.NotUsed]): S[Either[L1, R1], Mat] =
      typeClassInstance.flatMapConcat(self)(f)

    def eitherFlatMapAsyncUnordered[R1]
        (parallelism: Int)
        (f: R => Future[Either[L, R1]])
        (implicit ec: ExecutionContext): S[Either[L, R1], Mat] =
      typeClassInstance.eitherFlatMapAsyncUnordered(self)(parallelism)(f)(ec)

    def joinRight[L1 >: L, R1](implicit ev: R <:< Either[L1, R1]) : S[Either[L1, R1], Mat] =
      typeClassInstance.joinRight(self)

    def joinRight[L1, R1](convert: L => L1)(implicit ev: R <:< Either[L1, R1]): S[Either[L1, R1], Mat] =
      typeClassInstance.joinRight(self, convert)(ev)

    def joinLeft[L1, R1 >: R](implicit ev: L <:< Either[L1, R1]) : S[Either[L1, R1], Mat] =
      typeClassInstance.joinLeft(self)

    def joinLeft[L1, R1](convert: R => R1)(implicit ev: L <:< Either[L1, R1]): S[Either[L1, R1], Mat] =
      typeClassInstance.joinLeft(self, convert)(ev)

    def dropLeft(): S[R, Mat] =
      typeClassInstance.dropLeft(self)

    def dropRight(): S[L, Mat] =
      typeClassInstance.dropRight(self)
  }

  trait ToEitherStreamOps {
    implicit def eitherFlowStream[In, L, R, Mat]: EitherStream[Flow[In, ?, ?], L, R, Mat] = new EitherStream[Flow[In, ?, ?], L, R, Mat] {
      def leftFixed: Stream[Flow[In, ?, ?], Either[L, ?], R, Mat] =
        stream.flowStream[In, Either[L, ?], R, Mat]
      def unfixed: Stream[Flow[In, ?, ?], Id[?], Either[L, R], Mat] =
        stream.flowStream[In, Id[?], Either[L, R], Mat]
    }

    implicit def eitherSourceStream[L, R, Mat]: EitherStream[Source[?, ?], L, R, Mat] = new EitherStream[Source[?, ?], L, R, Mat] {
      def leftFixed: Stream[Source[?, ?], Either[L, ?], R, Mat] =
        stream.sourceStream[Either[L, ?], R, Mat]
      def unfixed: Stream[Source[?, ?], Id[?], Either[L, R], Mat] =
        stream.sourceStream[Id[?], Either[L, R], Mat]
    }
    implicit def toEitherStreamOps[S[_, _], L, R, Mat]
        (target: S[Either[L, R], Mat])
        (implicit tc: EitherStream[S, L, R, Mat]): Ops[S, L, R, Mat] = new Ops[S, L, R, Mat] {
      val self = target
      val typeClassInstance = tc
    }
    implicit def toEitherStreamOps[In, L, R, Mat]
        (target: Flow[In, Either[L, R], Mat])
        (implicit tc: EitherStream[Flow[In, ?, ?], L, R, Mat]): Ops[Flow[In, ?, ?], L, R, Mat] = new Ops[Flow[In, ?, ?], L, R, Mat] {
      val self = target
      val typeClassInstance = tc
    }
  }

  trait AllOps[S[_, _], L, R, Mat] extends Ops[S, L, R, Mat] {
    def typeClassInstance: EitherStream[S, L, R, Mat]
  }

  object ops {
    implicit def toAllEitherStreamOps[S[_, _], L, R, Mat]
        (target: S[Either[L, R], Mat])
        (implicit tc: EitherStream[S, L, R, Mat]): AllOps[S, L, R, Mat] = new AllOps[S, L, R, Mat] {
      val self = target
      val typeClassInstance = tc
    }
    implicit def toAllEitherStreamOps[In, L, R, Mat]
        (target: Flow[In, Either[L, R], Mat])
        (implicit tc: EitherStream[Flow[In, ?, ?], L, R, Mat]): AllOps[Flow[In, ?, ?], L, R, Mat] = new AllOps[Flow[In, ?, ?], L, R, Mat] {
      val self = target
      val typeClassInstance = tc
    }
  }
}
