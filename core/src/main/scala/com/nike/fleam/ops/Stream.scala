package com.nike.fleam
package ops

import org.apache.pekko.stream.{Graph, SourceShape}
import org.apache.pekko.stream.scaladsl._
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

trait Stream[S[_], M[_], A] {
  def map[B](stream: S[M[A]])(f: M[A] => M[B]): S[M[B]]
  def mapAsync[B](stream: S[M[A]])(parallelism: Int)(f: M[A] => Future[M[B]]): S[M[B]]
  def mapAsyncUnordered[B](stream: S[M[A]])(parallelism: Int)(f: M[A] => Future[M[B]]): S[M[B]]
  def flatMapConcat[B](stream: S[M[A]])(f: M[A] => Graph[SourceShape[M[B]], org.apache.pekko.NotUsed]): S[M[B]]
  def collect[B](stream: S[M[A]])(f: PartialFunction[M[A], M[B]]): S[M[B]]

  def mMap[B](stream: S[M[A]])(f: A => B)(implicit weakMonad: FlatMap[M]): S[M[B]] =
    map(stream)(m => weakMonad.map(m)(f))

  def mFlatMap[B](stream: S[M[A]])(f: A => M[B])(implicit weakMonad: FlatMap[M]): S[M[B]] =
    map(stream)(m => weakMonad.flatMap(m)(f))

  def mMapAsync[B]
      (stream: S[M[A]])
      (parallelism: Int)
      (f: A => Future[B])(implicit trav: Traverse[M], ec: ExecutionContext)
      : S[M[B]] =
    mapAsync(stream)(parallelism)(m => trav.traverse(m)(f))

  def mFlatMapAsync[B]
      (stream: S[M[A]])
      (parallelism: Int)
      (f: A => Future[M[B]])(implicit trav: Traverse[M], weakMonad: FlatMap[M], ec: ExecutionContext)
      : S[M[B]] =
    mapAsync(stream)(parallelism)(m => trav.flatTraverse(m)(f))

  def mMapAsyncUnordered[B]
      (stream: S[M[A]])
      (parallelism: Int)
      (f: A => Future[B])(implicit trav: Traverse[M], ec: ExecutionContext)
      : S[M[B]] =
    mapAsyncUnordered(stream)(parallelism)(m => trav.traverse(m)(f))

  def mFlatMapAsyncUnordered[B]
      (stream: S[M[A]])
      (parallelism: Int)
      (f: A => Future[M[B]])(implicit trav: Traverse[M], weakMonad: FlatMap[M], ec: ExecutionContext)
      : S[M[B]] =
    mapAsyncUnordered(stream)(parallelism)(m => trav.flatTraverse(m)(f))
}

object Stream {
  def apply[S[_], M[_], A](implicit instance: Stream[S, M, A]): Stream[S, M, A] = instance

  trait Ops[S[_], M[_], A] {
    def typeClassInstance: Stream[S, M, A]
    def self: S[M[A]]
    def map[B](f: M[A] => M[B]): S[M[B]] = typeClassInstance.map(self)(f)
    def mapAsync[B](parallelism: Int)(f: M[A] => Future[M[B]]): S[M[B]] = typeClassInstance.mapAsync(self)(parallelism)(f)
    def mapAsyncUnordered[B](parallelism: Int)(f: M[A] => Future[M[B]]): S[M[B]] = typeClassInstance.mapAsyncUnordered(self)(parallelism)(f)
    def flatMapConcat[B](f: M[A] => Graph[SourceShape[M[B]], org.apache.pekko.NotUsed]): S[M[B]] = typeClassInstance.flatMapConcat(self)(f)
    def collect[B](f: PartialFunction[M[A], M[B]]): S[M[B]] = typeClassInstance.collect(self)(f)
    def mMap[B](f: A => B)(implicit weakMonad: FlatMap[M]): S[M[B]] = typeClassInstance.mMap(self)(f)(weakMonad)
    def mFlatMap[B](f: A => M[B])(implicit weakMonad: FlatMap[M]): S[M[B]] = typeClassInstance.mFlatMap(self)(f)(weakMonad)
    def mMapAsync[B](parallelism: Int)(f: A => Future[B])(implicit trav: Traverse[M], ec: ExecutionContext) : S[M[B]] =
      typeClassInstance.mMapAsync(self)(parallelism)(f)(trav, ec)
    def mFlatMapAsync[B](parallelism: Int)(f: A => Future[M[B]])(implicit trav: Traverse[M], weakMonad: FlatMap[M], ec: ExecutionContext) : S[M[B]] =
      typeClassInstance.mFlatMapAsync(self)(parallelism)(f)(trav, weakMonad, ec)
    def mMapAsyncUnordered[B](parallelism: Int)(f: A => Future[B])(implicit trav: Traverse[M], ec: ExecutionContext) : S[M[B]] =
      typeClassInstance.mMapAsyncUnordered(self)(parallelism)(f)(trav, ec)
    def mFlatMapAsyncUnordered[B](parallelism: Int)(f: A => Future[M[B]])(implicit trav: Traverse[M], weakMonad: FlatMap[M], ec: ExecutionContext) : S[M[B]] =
      typeClassInstance.mFlatMapAsyncUnordered(self)(parallelism)(f)(trav, weakMonad, ec)
  }

  trait ToStreamOps {
    implicit def flowStream[In, M[_], A, Mat]: Stream[Flow[In, *, Mat], M, A] = new Stream[Flow[In, *, Mat], M, A] {
      def map[B](flow: Flow[In, M[A], Mat])(f: M[A] => M[B]): Flow[In, M[B], Mat] = flow.map(f)
      def mapAsync[B](flow: Flow[In, M[A], Mat])(parallelism: Int)(f: M[A] => Future[M[B]]): Flow[In, M[B], Mat] =
        flow.mapAsync(parallelism)(f)
      def mapAsyncUnordered[B](flow: Flow[In, M[A], Mat])(parallelism: Int)(f: M[A] => Future[M[B]]): Flow[In, M[B], Mat] =
        flow.mapAsyncUnordered(parallelism)(f)
      def flatMapConcat[B](flow: Flow[In, M[A], Mat])(f: M[A] => Graph[SourceShape[M[B]], org.apache.pekko.NotUsed]): Flow[In, M[B], Mat] =
        flow.flatMapConcat(f)
      def collect[B](flow: Flow[In, M[A], Mat])(f: PartialFunction[M[A], M[B]]): Flow[In, M[B], Mat] =
        flow.collect(f)
    }

    implicit def sourceStream[M[_], A, Mat]: Stream[Source[*, Mat], M, A] = new Stream[Source[*, Mat], M, A] {
      def map[B](source: Source[M[A], Mat])(f: M[A] => M[B]): Source[M[B], Mat] = source.map(f)
      def mapAsync[B](source: Source[M[A], Mat])(parallelism: Int)(f: M[A] => Future[M[B]]): Source[M[B], Mat] =
        source.mapAsync(parallelism)(f)
      def mapAsyncUnordered[B](source: Source[M[A], Mat])(parallelism: Int)(f: M[A] => Future[M[B]]): Source[M[B], Mat] =
        source.mapAsyncUnordered(parallelism)(f)
      def flatMapConcat[B](source: Source[M[A], Mat])(f: M[A] => Graph[SourceShape[M[B]], org.apache.pekko.NotUsed]): Source[M[B], Mat] =
        source.flatMapConcat(f)
      def collect[B](source: Source[M[A], Mat])(f: PartialFunction[M[A], M[B]]): Source[M[B], Mat] =
        source.collect(f)
    }

    implicit def toStreamOps[M[_], A, Mat]
      (target: Source[M[A], Mat])
      (implicit tc: Stream[Source[*, Mat], M, A]): Ops[Source[*, Mat], M, A] = new Ops[Source[*, Mat], M, A] {
      val self = target
      val typeClassInstance = tc
    }

    // Define a specific one for flow since it has 3 type arguments
    implicit def toStreamOps[In, M[_], A, Mat]
      (target: Flow[In, M[A], Mat])
      (implicit tc: Stream[Flow[In, *, Mat], M, A]): Ops[Flow[In, *, Mat], M, A] = new Ops[Flow[In, *, Mat], M, A] {
      val self = target
      val typeClassInstance = tc
    }
  }

  trait AllOps[S[_], M[_], A] extends Ops[S, M, A] {
    def typeClassInstance: Stream[S, M, A]
  }

  object ops {
    implicit def toAllStreamOps[S[_], M[_], A]
      (target: S[M[A]])
      (implicit tc: Stream[S, M, A]): AllOps[S, M, A] = new AllOps[S, M, A] {
      val self = target
      val typeClassInstance = tc
    }

    // Define a specific one for flow since it has 3 type arguments
    implicit def toAllStreamOps[In, M[_], A, Mat]
      (target: Flow[In, M[A], Mat])
      (implicit tc: Stream[Flow[In, *, Mat], M, A]): AllOps[Flow[In, *, Mat], M, A] = new AllOps[Flow[In, *, Mat], M, A] {
      val self = target
      val typeClassInstance = tc
    }
  }
}
