package com.nike.fleam
package logging

import configuration._

import akka.stream._
import akka.stream.scaladsl._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

object Counters {
  def countWithin[T](config: GroupedWithinConfiguration): Flow[T, Int, akka.NotUsed] =
    config.toTickingGroupedWithin.map(_.length)

  /** Count both sides of an Either independently
   *
   *  @param config A [[com.nike.fleam.configuration.GroupedWithinConfiguration]] that will be used to group each side
   *  of the Either independently.
   *
   *  @param leftMessage A function from Int to Out which is passed the count of left items processed and should produce the
   *  message.
   *
   *  @param rightMessage A function from Int to Out which is passed the count of right items processed and should produce the
   *  message.
   */
  def countEither[In <: Either[_, _], Out](
      config: GroupedWithinConfiguration)(
      leftMessage: Int => Out,
      rightMessage: Int => Out): Flow[In, Out, akka.NotUsed] = Flow.fromGraph {
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[In](2))
      val merge = builder.add(Merge[Out](2))
      val left = builder.add(
        Flow[In]
          .collect { case Left(failure) => failure }
          .via(Counters.countWithin(config))
          .map(leftMessage))
      val right = builder.add(
        Flow[In]
          .collect { case Right(success) => success }
          .via(Counters.countWithin(config))
          .map(rightMessage))

      broadcast ~> left ~> merge
      broadcast ~> right ~> merge
      FlowShape(broadcast.in, merge.out)
    }}
}

trait Counter[T, U] {
  def flow: Flow[T, U, akka.NotUsed]
}
