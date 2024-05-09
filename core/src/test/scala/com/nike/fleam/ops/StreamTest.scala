package com.nike.fleam
package ops

import org.apache.pekko.stream.scaladsl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import cats.implicits._
import implicits._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class StreamTest extends AnyFlatSpec with Matchers with ScalaFutures {

  import TestTools._

  it should "allow a flow to map over a monad" in {
    val flow = Flow[Option[Int]]
      .mMap(_ * 2)

    Source.single(Some(1)).via(flow).runWith(Sink.head).futureValue should be (Some(2))
  }

  it should "allow a source to map over a monad" in {
    val source = Source.single(Option(1))
      .mMap(_ * 2)

    source.runWith(Sink.head).futureValue should be (Some(2))
  }
}
