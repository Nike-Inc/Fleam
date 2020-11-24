package com.nike.fleam
package logging

import configuration._
import akka.stream.scaladsl._

import scala.concurrent.Promise
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import com.amazonaws.services.cloudwatch.model._

import scala.concurrent.duration._
import java.time.Instant
import java.util.Date

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class CloudWatchTest extends AnyFlatSpec with Matchers with ScalaFutures with IntegrationPatience {

  behavior of "CloudWatch"

  import TestTools.{ executionContext, materializer, checkSideEffect }

  it should "send a PutMetricDataRequest" in {
    val putRequest = Promise[PutMetricDataRequest]()
    val send = (request: PutMetricDataRequest) => { putRequest.success(request); new PutMetricDataResult }
    val logger = CloudWatch.metricsLogger(send)

    val now = Instant.now
    implicit val intCounter = new Counter[Int, PutMetricDataRequest] {
      val flow = Counters.countWithin[Int](GroupedWithinConfiguration(10, 1.seconds))
        .map(count => CloudWatch.wrap(
          namespace = "Test",
          metricName = "ItemsProcessed",
          count = count,
          now = now
        ))
    }

    val graph = Source(1 to 10)
      .via(logger.logCount)
      .runWith(Sink.ignore)

    val expected = new PutMetricDataRequest()
      .withNamespace("Test")
      .withMetricData(new MetricDatum()
        .withMetricName("ItemsProcessed")
        .withUnit(StandardUnit.Count)
        .withValue(10)
        .withTimestamp(new Date(now.toEpochMilli))
      )

    whenReady(checkSideEffect(graph, putRequest)) { _ shouldBe expected }
  }

  it should "include an instance dimension when requested" in {
    val putRequest = Promise[PutMetricDataRequest]()
    val send = (request: PutMetricDataRequest) => { putRequest.success(request); new PutMetricDataResult }
    val logger = CloudWatch.metricsLogger(send)

    val now = Instant.now
    implicit val intCounter = new Counter[Int, PutMetricDataRequest] {
      val flow = Counters.countWithin[Int](GroupedWithinConfiguration(10, 10.seconds))
        .map(count => CloudWatch.wrap(
          namespace = "Test",
          metricName = "ItemsProcessed",
          count = count,
          now = now,
          dimensions = List(CloudWatch.withInstanceId("i-1234567"))
        ))
    }

    val bareMetric = new MetricDatum()
      .withMetricName("ItemsProcessed")
      .withUnit(StandardUnit.Count)
      .withValue(10)
      .withTimestamp(new Date(now.toEpochMilli))

    val instanceMetric = new MetricDatum()
      .withMetricName("ItemsProcessed")
      .withDimensions(new Dimension()
        .withName("InstanceId")
        .withValue("i-1234567"))
      .withUnit(StandardUnit.Count)
      .withValue(10)
      .withTimestamp(new Date(now.toEpochMilli))

    val graph = Source(1 to 10)
      .via(logger.logCount)
      .runWith(Sink.ignore)

    whenReady(checkSideEffect(graph, putRequest)) { request =>
      request.getMetricData should contain (bareMetric)
      request.getMetricData should contain (instanceMetric)
    }
  }

  it should "include stack name dimension when requested" in {
    val putRequest = Promise[PutMetricDataRequest]()
    val send = (request: PutMetricDataRequest) => { putRequest.success(request); new PutMetricDataResult }
    val logger = CloudWatch.metricsLogger(send)

    val now = Instant.now
    implicit val intCounter = new Counter[Int, PutMetricDataRequest] {
      val flow = Counters.countWithin[Int](GroupedWithinConfiguration(10, 10.seconds))
        .map(count => CloudWatch.wrap(
          namespace = "Test",
          metricName = "ItemsProcessed",
          count = count,
          now = now,
          dimensions = List(CloudWatch.withStackName("MotivationalStack"))
        ))
    }

    val bareMetric = new MetricDatum()
      .withMetricName("ItemsProcessed")
      .withUnit(StandardUnit.Count)
      .withValue(10)
      .withTimestamp(new Date(now.toEpochMilli))

    val stackMetric = new MetricDatum()
      .withMetricName("ItemsProcessed")
      .withDimensions(new Dimension()
        .withName("StackName")
        .withValue("MotivationalStack"))
      .withUnit(StandardUnit.Count)
      .withValue(10)
      .withTimestamp(new Date(now.toEpochMilli))

    val graph = Source(1 to 10)
      .via(logger.logCount)
      .runWith(Sink.ignore)

    whenReady(checkSideEffect(graph, putRequest)) { request =>
      request.getMetricData should contain (bareMetric)
      request.getMetricData should contain (stackMetric)
    }
  }
}
