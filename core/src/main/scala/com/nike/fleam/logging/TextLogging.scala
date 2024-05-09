package com.nike.fleam
package logging

import configuration.GroupedWithinConfiguration
import org.apache.pekko.stream.scaladsl.Flow
import scala.concurrent.{ Future, ExecutionContext }

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

case class LogMessage(message: String)

object TextLogging {

  def apply(logger: String => Unit)(implicit ec: ExecutionContext) =
    metricsLogger(logger)

  def metricsLogger(logger: String => Unit)(implicit ec: ExecutionContext) =
    new MetricsLogger[LogMessage] {
      val client: Client = request => Future { logger(request.message) }
    }

  /** Logs the count of items to a function that takes String => Unit
   *
   *  @param logger function that takes the string produced
   *  @param config The grouping configuration, determines what the maximum number of items or maximum time before
   *    writing the message
   *  @param filter filters the items before counting them, *Does not filter the output of the stream*
   */
  def logCount[T](
      logger: String => Unit,
      config: GroupedWithinConfiguration,
      filter: T => Boolean = (_: T) => true)(message: Int => String)(implicit ec: ExecutionContext): Flow[T, T, org.apache.pekko.NotUsed] =
    metricsLogger(logger).logCount(filter) {
      new Counter[T, LogMessage] {
        val flow: Flow[T, LogMessage, org.apache.pekko.NotUsed] = Counters.countWithin(config).map(count => LogMessage(message(count)))
      }
    }
}
