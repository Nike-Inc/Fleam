package com.nike.fleam
package ops

import org.apache.pekko.stream.scaladsl._
import cats.implicits._
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

class EitherFlattenIterableTest extends AnyFlatSpec with Matchers with ScalaFutures {

  import TestTools._

  it should "flatten a source with an iterable right" in {

    val result = Source.single(List("asdf", "fdsa").asRight[Int])
      .eitherFlatten
      .runWith(Sink.seq)

    result.futureValue shouldBe List("asdf".asRight, "fdsa".asRight)
  }

  it should "flatten a flow with an iterable right" in {

    val flow = Flow[Either[Int, List[String]]]
      .eitherFlatten

    val result = Source.single(List("asdf", "fdsa").asRight[Int])
      .via(flow)
      .runWith(Sink.seq)

    result.futureValue shouldBe List("asdf".asRight, "fdsa".asRight)
  }

}
