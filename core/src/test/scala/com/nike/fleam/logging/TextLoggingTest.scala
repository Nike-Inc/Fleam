package com.nike.fleam
package logging

import configuration._
import akka.stream.scaladsl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}

import scala.concurrent.Promise
import scala.concurrent.duration._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class TextLoggingTest extends AnyFlatSpec with Matchers with ScalaFutures with IntegrationPatience {

  behavior of "TextLogging"

  import TestTools.{ executionContext, materializer, checkSideEffect }

  it should "write a LogMessage to a log function" in {
    val logWritten = Promise[String]
    val logger: String => Unit = { message => logWritten.success(message); () }

    val metricLogger = TextLogging(logger)

    implicit val intCounter = new Counter[Int, LogMessage] {
      def flow = Counters.countWithin[Int](GroupedWithinConfiguration(10, 1.seconds))
        .map(count => LogMessage(s"Processed $count items"))
    }

    val graph = Source.single(1)
      .via(metricLogger.logCount)
      .runWith(Sink.ignore)

    whenReady(checkSideEffect(graph, logWritten)) { _ should be ("Processed 1 items") }
  }
}
