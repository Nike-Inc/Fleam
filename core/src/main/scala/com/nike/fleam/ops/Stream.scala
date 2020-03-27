package com.nike.fleam
package ops

import akka.stream.{Graph, SourceShape}
import akka.stream.scaladsl._
import cats._
import cats.implicits._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

trait Stream[S[_, _], M[_], A, Mat] {
  def map[B](stream: S[M[A], Mat])(f: M[A] => M[B]): S[M[B], Mat]
  def mapAsync[B](stream: S[M[A], Mat])(parallelism: Int)(f: M[A] => Future[M[B]]): S[M[B], Mat]
  def mapAsyncUnordered[B](stream: S[M[A], Mat])(parallelism: Int)(f: M[A] => Future[M[B]]): S[M[B], Mat]
  def flatMapConcat[B](stream: S[M[A], Mat])(f: M[A] => Graph[SourceShape[M[B]], akka.NotUsed]): S[M[B], Mat]
  def collect[B](stream: S[M[A], Mat])(f: PartialFunction[M[A], M[B]]): S[M[B], Mat]

  def mMap[B](stream: S[M[A], Mat])(f: A => B)(implicit weakMonad: FlatMap[M]): S[M[B], Mat] =
    map(stream)(m => weakMonad.map(m)(f))

  def mFlatMap[B](stream: S[M[A], Mat])(f: A => M[B])(implicit weakMonad: FlatMap[M]): S[M[B], Mat] =
    map(stream)(m => weakMonad.flatMap(m)(f))

  def mMapAsync[B]
      (stream: S[M[A], Mat])
      (parallelism: Int)
      (f: A => Future[B])(implicit trav: Traverse[M], ec: ExecutionContext)
      : S[M[B], Mat] =
    mapAsync(stream)(parallelism)(m => trav.traverse(m)(f))

  def mFlatMapAsync[B]
      (stream: S[M[A], Mat])
      (parallelism: Int)
      (f: A => Future[M[B]])(implicit trav: Traverse[M], weakMonad: FlatMap[M], ec: ExecutionContext)
      : S[M[B], Mat] =
    mapAsync(stream)(parallelism)(m => trav.flatTraverse(m)(f))

  def mMapAsyncUnordered[B]
      (stream: S[M[A], Mat])
      (parallelism: Int)
      (f: A => Future[B])(implicit trav: Traverse[M], ec: ExecutionContext)
      : S[M[B], Mat] =
    mapAsyncUnordered(stream)(parallelism)(m => trav.traverse(m)(f))

  def mFlatMapAsyncUnordered[B]
      (stream: S[M[A], Mat])
      (parallelism: Int)
      (f: A => Future[M[B]])(implicit trav: Traverse[M], weakMonad: FlatMap[M], ec: ExecutionContext)
      : S[M[B], Mat] =
    mapAsyncUnordered(stream)(parallelism)(m => trav.flatTraverse(m)(f))
}

object Stream {
  def apply[S[_, _], M[_], A, Mat](implicit instance: Stream[S, M, A, Mat]): Stream[S, M, A, Mat] = instance

  trait Ops[S[_, _], M[_], A, Mat] {
    def typeClassInstance: Stream[S, M, A, Mat]
    def self: S[M[A], Mat]
    def map[B](f: M[A] => M[B]): S[M[B], Mat] = typeClassInstance.map(self)(f)
    def mapAsync[B](parallelism: Int)(f: M[A] => Future[M[B]]): S[M[B], Mat] = typeClassInstance.mapAsync(self)(parallelism)(f)
    def mapAsyncUnordered[B](parallelism: Int)(f: M[A] => Future[M[B]]): S[M[B], Mat] = typeClassInstance.mapAsyncUnordered(self)(parallelism)(f)
    def flatMapConcat[B](f: M[A] => Graph[SourceShape[M[B]], akka.NotUsed]): S[M[B], Mat] = typeClassInstance.flatMapConcat(self)(f)
    def collect[B](f: PartialFunction[M[A], M[B]]): S[M[B], Mat] = typeClassInstance.collect(self)(f)
    def mMap[B](f: A => B)(implicit weakMonad: FlatMap[M]): S[M[B], Mat] = typeClassInstance.mMap(self)(f)(weakMonad)
    def mFlatMap[B](f: A => M[B])(implicit weakMonad: FlatMap[M]): S[M[B], Mat] = typeClassInstance.mFlatMap(self)(f)(weakMonad)
    def mMapAsync[B](parallelism: Int)(f: A => Future[B])(implicit trav: Traverse[M], ec: ExecutionContext) : S[M[B], Mat] =
      typeClassInstance.mMapAsync(self)(parallelism)(f)(trav, ec)
    def mFlatMapAsync[B](parallelism: Int)(f: A => Future[M[B]])(implicit trav: Traverse[M], weakMonad: FlatMap[M], ec: ExecutionContext) : S[M[B], Mat] =
      typeClassInstance.mFlatMapAsync(self)(parallelism)(f)(trav, weakMonad, ec)
    def mMapAsyncUnordered[B](parallelism: Int)(f: A => Future[B])(implicit trav: Traverse[M], ec: ExecutionContext) : S[M[B], Mat] =
      typeClassInstance.mMapAsyncUnordered(self)(parallelism)(f)(trav, ec)
    def mFlatMapAsyncUnordered[B](parallelism: Int)(f: A => Future[M[B]])(implicit trav: Traverse[M], weakMonad: FlatMap[M], ec: ExecutionContext) : S[M[B], Mat] =
      typeClassInstance.mFlatMapAsyncUnordered(self)(parallelism)(f)(trav, weakMonad, ec)
  }

  trait ToStreamOps {
    implicit def flowStream[In, M[_], A, Mat]: Stream[Flow[In, ?, ?], M, A, Mat] = new Stream[Flow[In, ?, ?], M, A, Mat] {
      def map[B](flow: Flow[In, M[A], Mat])(f: M[A] => M[B]): Flow[In, M[B], Mat] = flow.map(f)
      def mapAsync[B](flow: Flow[In, M[A], Mat])(parallelism: Int)(f: M[A] => Future[M[B]]): Flow[In, M[B], Mat] =
        flow.mapAsync(parallelism)(f)
      def mapAsyncUnordered[B](flow: Flow[In, M[A], Mat])(parallelism: Int)(f: M[A] => Future[M[B]]): Flow[In, M[B], Mat] =
        flow.mapAsyncUnordered(parallelism)(f)
      def flatMapConcat[B](flow: Flow[In, M[A], Mat])(f: M[A] => Graph[SourceShape[M[B]], akka.NotUsed]): Flow[In, M[B], Mat] =
        flow.flatMapConcat(f)
      def collect[B](flow: Flow[In, M[A], Mat])(f: PartialFunction[M[A], M[B]]): Flow[In, M[B], Mat] =
        flow.collect(f)
    }

    implicit def sourceStream[M[_], A, Mat]: Stream[Source[?, ?], M, A, Mat] = new Stream[Source[?, ?], M, A, Mat] {
      def map[B](source: Source[M[A], Mat])(f: M[A] => M[B]): Source[M[B], Mat] = source.map(f)
      def mapAsync[B](source: Source[M[A], Mat])(parallelism: Int)(f: M[A] => Future[M[B]]): Source[M[B], Mat] =
        source.mapAsync(parallelism)(f)
      def mapAsyncUnordered[B](source: Source[M[A], Mat])(parallelism: Int)(f: M[A] => Future[M[B]]): Source[M[B], Mat] =
        source.mapAsyncUnordered(parallelism)(f)
      def flatMapConcat[B](source: Source[M[A], Mat])(f: M[A] => Graph[SourceShape[M[B]], akka.NotUsed]): Source[M[B], Mat] =
        source.flatMapConcat(f)
      def collect[B](source: Source[M[A], Mat])(f: PartialFunction[M[A], M[B]]): Source[M[B], Mat] =
        source.collect(f)
    }

    implicit def toStreamOps[S[_, _], M[_], A, Mat]
      (target: S[M[A], Mat])
      (implicit tc: Stream[S, M, A, Mat]): Ops[S, M, A, Mat] = new Ops[S, M, A, Mat] {
      val self = target
      val typeClassInstance = tc
    }

    // Define a specific one for flow since it has 3 type arguments
    implicit def toStreamOps[In, M[_], A, Mat]
      (target: Flow[In, M[A], Mat])
      (implicit tc: Stream[Flow[In, ?, ?], M, A, Mat]): Ops[Flow[In, ?, ?], M, A, Mat] = new Ops[Flow[In, ?, ?], M, A, Mat] {
      val self = target
      val typeClassInstance = tc
    }
  }

  trait AllOps[S[_, _], M[_], A, Mat] extends Ops[S, M, A, Mat]{
    def typeClassInstance: Stream[S, M, A, Mat]
  }

  object ops {
    implicit def toAllStreamOps[S[_, _], M[_], A, Mat]
      (target: S[M[A], Mat])
      (implicit tc: Stream[S, M, A, Mat]): AllOps[S, M, A, Mat] = new AllOps[S, M, A, Mat] {
      val self = target
      val typeClassInstance = tc
    }

    // Define a specific one for flow since it has 3 type arguments
    implicit def toAllStreamOps[In, M[_], A, Mat]
      (target: Flow[In, M[A], Mat])
      (implicit tc: Stream[Flow[In, ?, ?], M, A, Mat]): AllOps[Flow[In, ?, ?], M, A, Mat] = new AllOps[Flow[In, ?, ?], M, A, Mat] {
      val self = target
      val typeClassInstance = tc
    }
  }
}
