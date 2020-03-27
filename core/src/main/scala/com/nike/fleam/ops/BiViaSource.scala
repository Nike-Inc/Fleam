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

trait BiViaSourceOps {
  implicit def biViaSourceOps[L, R](source: Source[Either[L, R], akka.NotUsed]): BiViaSource[L, R] =
    new BiViaSource(source)
}

object BiViaSourceOps extends BiViaSourceOps

class BiViaSource[L, R](val source: Source[Either[L, R], akka.NotUsed]) extends AnyVal {
  /** Process each side of an Either through a separate Flows and then combine the result back into an Either
   *  Does not preserve order
   */
  def biVia[L1, R1](left: Flow[L, L1, akka.NotUsed], right: Flow[R, R1, akka.NotUsed]): Source[Either[L1, R1], akka.NotUsed] =
    Source.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val partition = builder.add(Partition[Either[L, R]](2, _.fold(_ => 0, _ => 1)))
      val leftFlow = builder.add(left)
      val rightFlow = builder.add(right)
      val leftSeparate = builder.add(Flow[Either[L, R]].collect { case Left(lIn) => lIn })
      val rightSeparate = builder.add(Flow[Either[L, R]].collect { case Right(rIn) => rIn })
      val leftLift = builder.add(Flow[L1].map(Left[L1, R1](_)))
      val rightLift = builder.add(Flow[R1].map(Right[L1, R1](_)))
      val merge = builder.add(Merge[Either[L1, R1]](2))

      builder.add(source) ~> partition
                             partition.out(0) ~> leftSeparate ~> leftFlow ~> leftLift ~> merge
                             partition.out(1) ~> rightSeparate ~> rightFlow ~> rightLift ~> merge

      SourceShape(merge.out)
    })

  /** Process right side of Either through a flow
   *  Does not preserve order
   */
  def viaRight[R1](right: Flow[R, R1, akka.NotUsed]) = biVia[L, R1](left = Flow[L], right = right)

  /** Process left side of Either through a flow
   *  Does not preserve order
   */
  def viaLeft[L1](left: Flow[L, L1, akka.NotUsed]) = biVia[L1, R](left = left, right = Flow[R])
}
