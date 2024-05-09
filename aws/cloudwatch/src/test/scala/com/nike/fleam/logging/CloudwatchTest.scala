package com.nike.fleam.cloudwatch

import com.nike.fleam.configuration._
import com.nike.fleam.logging.{Counter, Counters}
import org.apache.pekko.stream.scaladsl._

import scala.concurrent.Promise
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import software.amazon.awssdk.services.cloudwatch.model._

import scala.concurrent.Future
import scala.concurrent.duration._
import java.time.Instant

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class CloudWatchTest extends AnyFlatSpec with Matchers with ScalaFutures with IntegrationPatience {

  behavior of "CloudWatch"

  import com.nike.fleam.TestTools.{ executionContext, materializer, checkSideEffect }

  it should "send a PutMetricDataRequest" in {
    val putRequest = Promise[PutMetricDataRequest]()
    val send = (request: PutMetricDataRequest) => {
      putRequest.success(request)
      Future.successful(PutMetricDataResponse.builder.build())
    }
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

    val expected = PutMetricDataRequest.builder()
      .namespace("Test")
      .metricData(MetricDatum.builder()
        .metricName("ItemsProcessed")
        .unit(StandardUnit.COUNT)
        .value(10)
        .timestamp(now)
        .build()
      )
      .build()

    whenReady(checkSideEffect(graph, putRequest)) { _ shouldBe expected }
  }

  it should "include an instance dimension when requested" in {
    val putRequest = Promise[PutMetricDataRequest]()
    val send = (request: PutMetricDataRequest) => {
      putRequest.success(request)
      Future.successful(PutMetricDataResponse.builder.build())
    }
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

    val bareMetric = MetricDatum.builder()
      .metricName("ItemsProcessed")
      .unit(StandardUnit.COUNT)
      .value(10)
      .timestamp(now)
      .build()

    val instanceMetric = MetricDatum.builder()
      .metricName("ItemsProcessed")
      .dimensions(Dimension.builder()
        .name("InstanceId")
        .value("i-1234567")
        .build())
      .unit(StandardUnit.COUNT)
      .value(10)
      .timestamp(now)
      .build()

    val graph = Source(1 to 10)
      .via(logger.logCount)
      .runWith(Sink.ignore)

    whenReady(checkSideEffect(graph, putRequest)) { request =>
      request.metricData should contain (bareMetric)
      request.metricData should contain (instanceMetric)
    }
  }

  it should "include stack name dimension when requested" in {
    val putRequest = Promise[PutMetricDataRequest]()
    val send = (request: PutMetricDataRequest) => {
      putRequest.success(request)
      Future.successful(PutMetricDataResponse.builder.build())
    }

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

    val bareMetric = MetricDatum.builder()
      .metricName("ItemsProcessed")
      .unit(StandardUnit.COUNT)
      .value(10)
      .timestamp(now)
      .build()

    val stackMetric = MetricDatum.builder()
      .metricName("ItemsProcessed")
      .dimensions(Dimension.builder()
        .name("StackName")
        .value("MotivationalStack")
        .build())
      .unit(StandardUnit.COUNT)
      .value(10)
      .timestamp(now)
      .build()

    val graph = Source(1 to 10)
      .via(logger.logCount)
      .runWith(Sink.ignore)

    whenReady(checkSideEffect(graph, putRequest)) { request =>
      request.metricData should contain (bareMetric)
      request.metricData should contain (stackMetric)
    }
  }
}
