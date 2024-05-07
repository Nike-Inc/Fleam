package com.nike.fleam.cloudwatch

import org.apache.pekko.stream.scaladsl.Flow
import com.nike.fleam.configuration.GroupedWithinConfiguration
import com.nike.fleam.logging.{Counter, Counters, MetricsLogger}
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import software.amazon.awssdk.services.cloudwatch.model._
import software.amazon.awssdk.regions.internal.util.EC2MetadataUtils
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

object CloudWatch {

  def apply(awsClient: CloudWatchAsyncClient)(implicit ec: ExecutionContext): MetricsLogger[PutMetricDataRequest] =
    metricsLogger(send = wrapRequest[PutMetricDataRequest, PutMetricDataResponse](awsClient.putMetricData))

  def metricsLogger(send: PutMetricDataRequest => Future[PutMetricDataResponse])(implicit ec: ExecutionContext) =
    new MetricsLogger[PutMetricDataRequest] {
      val client: Client = send(_).map(_ => ())
    }

  type DimensionalMetric = (String, Int, Instant) => MetricDatum

  def withDimensions(dimensions: Map[String, String]): DimensionalMetric = { (metricName: String, count: Int, now: Instant) =>
    val datum = MetricDatum.builder()
      .metricName(metricName)
      .value(count)
      .timestamp(now)
      .build()

    dimensions.foldLeft(datum) { case (data, (key, value)) =>
      data.toBuilder()
        .dimensions(Dimension.builder()
          .name(key)
          .value(value)
          .build())
        .unit(StandardUnit.COUNT)
        .build()
    }
  }

  def withStackName(stackName: String):DimensionalMetric = withDimensions(Map("StackName" -> stackName))

  def withInstanceId(instanceId: String): DimensionalMetric = withDimensions(Map("InstanceId" -> instanceId))

  def wrap(namespace: String, metricName: String, count: Int, now: Instant = Instant.now, dimensions: List[DimensionalMetric] = List.empty) = {
    val datum = MetricDatum.builder()
      .metricName(metricName)
      .unit(StandardUnit.COUNT)
      .value(count)
      .timestamp(now)
      .build() :: dimensions.map(_(metricName, count, now))

    PutMetricDataRequest.builder()
      .namespace(namespace)
      .metricData(datum :_*)
      .build()
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
    awsClient: CloudWatchAsyncClient,
    config: GroupedWithinConfiguration,
    dimensions: List[DimensionalMetric] =
      List(CloudWatch.withInstanceId(Option(EC2MetadataUtils.getInstanceId).getOrElse("missing"))),
    filter: T => Boolean = (_: T) => true,
    now: () => Instant = () => Instant.now())(metricName: String)(implicit ec: ExecutionContext)
    : Flow[T, T, org.apache.pekko.NotUsed] =
    CloudWatch
      .metricsLogger(send = wrapRequest(awsClient.putMetricData))
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
