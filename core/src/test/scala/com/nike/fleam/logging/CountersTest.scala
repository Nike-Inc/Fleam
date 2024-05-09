package com.nike.fleam
package logging

import configuration._
import org.apache.pekko.stream.scaladsl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class CountersTest extends AnyFlatSpec with Matchers with ScalaFutures with IntegrationPatience {

  behavior of "Counters"

  import TestTools.{ executionContext, materializer }

  "countEither" should "produce messages for both sides of an either" in {

    val eithers = List[Either[String, Int]](
      Right(1),
      Left("asdf"),
      Right(2),
      Right(3),
      Left("qwer"))

    implicit val counter = new Counter[Either[String, Int], LogMessage] {
      val flow = Counters.countEither(config = GroupedWithinConfiguration(batchSize = 10, within = 1.seconds))(
        leftMessage = count => LogMessage(s"Counted $count Strings"),
        rightMessage = count => LogMessage(s"Counted $count Ints"))
    }

    val messagesReceived = Promise[List[String]]()
    var messages = List.empty[String]

    val writeLogMessage = (message: String) => {
      messages = messages :+ message
      if (messages.length == 2) messagesReceived.success(messages.toList)
      ({})
    }

    val logger = TextLogging(logger = writeLogMessage).logCount[Either[String, Int]]

    Source(eithers)
      .via(logger)
      .runWith(Sink.ignore)

    messagesReceived.future.futureValue should contain theSameElementsAs List("Counted 2 Strings", "Counted 3 Ints")
  }

  "countWithin" should "tick counts even when no values are passing through" in {
    val counter = Counters.countWithin[String](GroupedWithinConfiguration(batchSize = 100, within = 100.millis))

    val result = Source.future(Future { Thread.sleep(1000); "asdf!" })
      .filter(_ => false)
      .via(counter)
      .runWith(Sink.seq)

    whenReady(result) { counts =>
      counts shouldNot be(List.empty)
      assert(counts.forall(_ == 0))
    }
  }
}
