package com.nike.fleam
package ops

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.apache.pekko.stream.scaladsl._
import implicits._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class TupleFlowHelperTest extends AnyFlatSpec with Matchers with ScalaFutures with IntegrationPatience {

  behavior of "TupleFlowHelper"

  import TestTools._

  "TupleFlowHelper" should "let you map over the right side of a tuple" in {
    val source = Source.single(("asdf", 1))

    val flow: Flow[(String, Int), (String, Int), _] = Flow[(String, Int)]
      .mapRight(i => i * 2)

    val result = source.via(flow).runWith(Sink.head)

    whenReady(result) { _ should be(("asdf", 2)) }
  }

  "TupleFlowHelper" should "let you map over the left side of a tuple" in {
    val source = Source.single(("asdf", 1))

    val flow: Flow[(String, Int), (String, Int), _] = Flow[(String, Int)]
      .mapLeft(text => text.toUpperCase)

    val result = source.via(flow).runWith(Sink.head)

    whenReady(result) { _ should be(("ASDF", 1)) }
  }

  "TupleSourceHelper" should "let you map over the right side of a tuple" in {
    val result = Source.single(("asdf", 1))
      .mapRight(i => i * 2)
      .runWith(Sink.head)

    whenReady(result) { _ should be(("asdf", 2)) }
  }

  "TupleSourceHelper" should "let you map over the left side of a tuple" in {
    val result = Source.single(("asdf", 1))
      .mapLeft(text => text.toUpperCase)
      .runWith(Sink.head)

    whenReady(result) { _ should be(("ASDF", 1)) }
  }
}
