package com.nike.fleam
package ops

import akka.stream.scaladsl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import implicits._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class BroadcastMergeFlowTest extends AnyFlatSpec with Matchers with ScalaFutures {

  import TestTools._

  it should "broadcastMerge a Flow" in {
    val double: Flow[Int, Double, akka.NotUsed] = Flow[Int].map(_ * 2.0)
    val halve: Flow[Int, Double, akka.NotUsed] = Flow[Int].map(_ / 2.0)
    val squareRoot: Flow[Int, Double, akka.NotUsed] = Flow[Int].map(Math.sqrt(_))

    val flow = Flow[Int].broadcastMerge(double, halve, squareRoot)
    val results = Source.single(10).via(flow).runWith(Sink.seq)

    whenReady(results) { _ should contain theSameElementsAs(List(20.0, 5.0, 3.1622776601683795)) }
  }
}
