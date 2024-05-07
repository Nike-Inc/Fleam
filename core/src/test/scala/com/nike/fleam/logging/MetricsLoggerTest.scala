package com.nike.fleam
package logging

import configuration._
import org.apache.pekko.stream.scaladsl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class MetricsLoggerTest extends AnyFlatSpec with Matchers with ScalaFutures with IntegrationPatience {

  behavior of "MetricsLogger"

  import TestTools.{ executionContext, materializer }

  it should "send messages to the count items and send them to the log" in {
    val items = Promise[List[Int]]()
    var requests = List.empty[Int]

    val testLogger = new MetricsLogger[Int] {
      val client: Client = request => Future {
        requests = requests :+ request
        if (requests.length == 2) items.success(requests.toList)
      }
    }

    implicit val intTestLogger = new Counter[Int, Int] {
      def flow = Counters.countWithin(GroupedWithinConfiguration(5, 1.seconds))
    }

    Source(1 to 10)
      .via(testLogger.logCount)
      .runWith(Sink.ignore)

    items.future.futureValue shouldBe List(5, 5)
  }

  it should "let you filter and not lose elements from the stream" in {
    val countsReceived = Promise[Int]()

    val testLogger = new MetricsLogger[Int] {
      val client: Client = request => Future {
        countsReceived.success(request)
      }
    }

    implicit val intTestLogger = new Counter[Int, Int] {
      def flow = Counters.countWithin(GroupedWithinConfiguration(5, 10.millis))
    }

    val graph = Source(1 to 10)
      .via(testLogger.logCount(_ > 9))
      .runWith(Sink.seq)

    countsReceived.future.futureValue shouldBe 1 // Should only count one thing, 10
    graph.futureValue should contain theSameElementsAs (1 to 10)
  }
}
