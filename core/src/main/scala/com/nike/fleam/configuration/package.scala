package com.nike.fleam

import scala.concurrent.duration._
import akka.pattern.CircuitBreaker
import akka.stream.scaladsl.Flow
import akka.actor.ActorSystem
import ops.TickingGroupedWithinFlowOps._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

package object configuration {
  implicit class CircuitBreakerConfigHelper(val config: CircuitBreakerConfiguration) extends AnyVal {
    def toCircuitBreaker(implicit system: ActorSystem) =
      CircuitBreaker(system.scheduler, config.maxFailures, config.callTimeout, config.resetTimeout)
  }

  implicit class GroupedWithinConfigHelper(val config: GroupedWithinConfiguration) extends AnyVal {
    def toFlow[T] = Flow[T].groupedWithin(config.batchSize, config.within)
    def toTickingGroupedWithin[T] = Flow[T].tickingGroupedWithin(config.batchSize, config.within)
  }
}

package configuration {

  case class GroupedWithinConfiguration(batchSize: Int, within: FiniteDuration)
  case class ThrottleConfiguration(elements: Int, per: FiniteDuration, maximumBurst: Int)
  case class CircuitBreakerConfiguration(maxFailures: Int, callTimeout: FiniteDuration, resetTimeout: FiniteDuration)
}
