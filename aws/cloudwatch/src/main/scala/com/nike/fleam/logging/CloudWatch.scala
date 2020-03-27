package com.nike.fleam
package logging

import akka.stream.scaladsl.Flow
import configuration.GroupedWithinConfiguration
import com.amazonaws.services.cloudwatch.AmazonCloudWatch
import com.amazonaws.services.cloudwatch.model._
import com.amazonaws.util.EC2MetadataUtils
import java.time.Instant
import java.util.Date

import scala.concurrent.{ExecutionContext, Future}

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

object CloudWatch {

  def apply(awsClient: AmazonCloudWatch)(implicit ec: ExecutionContext): MetricsLogger[PutMetricDataRequest] =
    metricsLogger(send = awsClient.putMetricData)

  def metricsLogger(send: PutMetricDataRequest => PutMetricDataResult)(implicit ec: ExecutionContext) =
    new MetricsLogger[PutMetricDataRequest] {
      val client: Client = request => Future { send(request) }
    }

  type DimensionalMetric = (String, Int, Date) => MetricDatum

  def withDimensions(dimensions: Map[String, String]): DimensionalMetric = { (metricName: String, count: Int, now: Date) =>
    val datum = new MetricDatum().withMetricName(metricName).withValue(count).withTimestamp(now)
    dimensions.foldLeft(datum) { case (data, (key, value)) =>
      data
        .withDimensions(new Dimension()
          .withName(key)
          .withValue(value))
        .withUnit(StandardUnit.Count)
    }
  }

  def withStackName(stackName: String):DimensionalMetric = withDimensions(Map("StackName" -> stackName))

  def withInstanceId(instanceId: String): DimensionalMetric = withDimensions(Map("InstanceId" -> instanceId))

  def wrap(namespace: String, metricName: String, count: Int, now: Instant = Instant.now, dimensions: List[DimensionalMetric] = List.empty) = {
    val time = new Date(now.toEpochMilli)
    val datum = new MetricDatum()
      .withMetricName(metricName)
      .withUnit(StandardUnit.Count)
      .withValue(count)
      .withTimestamp(time) :: dimensions.map(_(metricName, count, time))
    new PutMetricDataRequest().withNamespace(namespace).withMetricData(datum :_*)
  }

  /** Logs the count of items to Cloudwatch
   *
   *  @param namespace cloudwatch namespace to write metrics to
   *  @param awsClient An AWS cloudwatch client
   *  @param config The grouping configuration, determines what the maximum number of items or maximum time before
   *    writing the message
   *  @param dimensions a list of dimensions to include with the metric written
   *  @param filter filters the items before counting them, *Does not filter the output of the stream*
   *  @param now a function that determines the current time
   */
  def logCount[T](
    namespace: String,
    awsClient: AmazonCloudWatch,
    config: GroupedWithinConfiguration,
    dimensions: List[DimensionalMetric] =
      List(CloudWatch.withInstanceId(Option(EC2MetadataUtils.getInstanceId).getOrElse("missing"))),
    filter: T => Boolean = (_: T) => true,
    now: () => Instant = () => Instant.now())(metricName: String)(implicit ec: ExecutionContext)
    : Flow[T, T, akka.NotUsed] =
    CloudWatch
      .metricsLogger(send = awsClient.putMetricData)
      .logCount(filter) { new Counter[T, PutMetricDataRequest] {
        val flow = Counters
          .countWithin[T](config)
          .map(
            count =>
              CloudWatch.wrap(
                namespace = namespace,
                metricName = metricName,
                count = count,
                now = now(),
                dimensions = dimensions))
      }}
}
