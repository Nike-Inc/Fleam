package com.nike.fleam
package ops

import akka.stream.scaladsl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import com.nike.fleam.implicits._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class TickingGroupedWithinFlowTest extends AnyFlatSpec with Matchers with ScalaFutures {

  import TestTools._

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 20.seconds, interval = 10.millis)

  it should "tick groups even when no values are passing from a flow" in {

    val flow: Flow[Int, Seq[Int], akka.NotUsed] =
      Flow[Int]
        .tickingGroupedWithin(batchSize = 100, within = 100.millis)

    val receivedGroup = Promise[Seq[Int]]()

    Source.future(Future { Thread.sleep(2000); 1 })
      .via(flow)
      .take(10)
      .map { group =>
        if (!receivedGroup.isCompleted) receivedGroup.success(group)
        group
      }
      .runWith(Sink.seq)

    receivedGroup.future.futureValue shouldBe Seq.empty
  }
}
