package com.nike.fleam
package ops

import org.apache.pekko.stream.scaladsl._
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

class BiViaSourceTest extends AnyFlatSpec with Matchers with ScalaFutures {

  import TestTools._

  it should "biVia an Either Source" in {
    val double: Flow[Int, Int, org.apache.pekko.NotUsed] = Flow[Int].map(_ * 2)
    val upperCase: Flow[String, String, org.apache.pekko.NotUsed] = Flow[String].map(_.toUpperCase)

    val testRight = Source.single(Right("asdf")).biVia(double, upperCase).runWith(Sink.head)
    val testLeft = Source.single(Left(1)).biVia(double, upperCase).runWith(Sink.head)

    whenReady(testRight) { _ should be(Right("ASDF")) }
    whenReady(testLeft) { _ should be(Left(2)) }
  }

  it should "viaRight an Either Source" in {
    val upperCase: Flow[String, String, org.apache.pekko.NotUsed] = Flow[String].map(_.toUpperCase)

    val testRight = Source.single(Right("asdf")).viaRight(upperCase).runWith(Sink.head)
    val testLeft = Source.single(Left(1)).viaRight(upperCase).runWith(Sink.head)

    whenReady(testRight) { _ should be(Right("ASDF")) }
    whenReady(testLeft) { _ should be(Left(1)) }
  }

  it should "viaLeft an Either Source" in {
    val double: Flow[Int, Int, org.apache.pekko.NotUsed] = Flow[Int].map(_ * 2)

    val testRight = Source.single(Right("asdf")).viaLeft(double).runWith(Sink.head)
    val testLeft = Source.single(Left(1)).viaLeft(double).runWith(Sink.head)

    whenReady(testRight) { _ should be(Right("asdf")) }
    whenReady(testLeft) { _ should be(Left(2)) }
  }
}
