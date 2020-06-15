package com.nike.fleam

import akka.actor.ActorSystem
import akka.pattern.{ CircuitBreaker, after }
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Future
import scala.concurrent.duration._
import cats.~>

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

/** A Valve slows down the reqeusts to an external service.
 *
 *  When making calls to an external service you can use a Valve to slow the processing requests if the circuit breaker
 *  has opened. Normally when a circuit breaker opens real-time requests fail quickly to protect the downstream service from being
 *  overloaded. In the case of processing a stream we just want to slow down processing until the downstream service comes back up.
 */
object Valve extends ValveLiftedFailedValuesPoly {
  type MaxRetries = Int
  type CurrentRetry = Int
  type DelayCalculation = (MaxRetries, CurrentRetry) => FiniteDuration

  def exponentialBackoff(time: FiniteDuration): DelayCalculation = { (maxRetires, currentRetry) =>
    FiniteDuration(
      length = math.pow(time.length, currentRetry).toLong,
      unit = time.unit
    )
  }

  def constant(time: FiniteDuration): DelayCalculation = { (maxRetries, currentRetry) => time }

  def multipled(time: FiniteDuration): DelayCalculation = { (maxRetries, currentRetry) =>
    time * currentRetry
  }

  /** Lifts certain values into exception based world of CircuitBreakers.
   *  This allows you to trigger failures in the circuit breaker based on the matched values in a partial function
   *
   *  Example: Valve(...)(Valve.liftFailedValues({ case Left(something) => new Exception(...) })(myCall))(myValue)
   */
  def liftFailedValues[U](policy: PartialFunction[U, Exception]) = new LiftedFailedValues(policy)


  def apply(
    circuitBreaker: CircuitBreaker,
    maxRetries: Int,
    delay: DelayCalculation = exponentialBackoff(2.seconds),
    logger: String => Unit = _ => ())
    (implicit actorSystem: ActorSystem) = {
      val circuitBreakerTransformation = new (Future ~> Future) {
        def apply[T](f: Future[T]): Future[T] = circuitBreaker.withCircuitBreaker(f)
      }
      new Valve(circuitBreakerTransformation, maxRetries, delay, logger)
    }
}

class Valve(
  circuitBreaker: Future ~> Future,
  maxRetries: Int,
  delay: Valve.DelayCalculation,
  logger: String => Unit = _ => ())
    (implicit actorSystem: ActorSystem) extends ValvePoly {

  protected def applyOne[T, U](f: T => Future[U], recoverWith: PartialFunction[Throwable, Future[U]]) = { t: T =>
    import actorSystem.dispatcher
    val scheduler = actorSystem.scheduler
    def tryCall(n: Int): Future[U] = {
      circuitBreaker {
        f(t)
      }.recoverWith { case thrown =>
        if (n <= 0) {
          val default: Throwable => Future[U] = ex => Future.failed(ex)
          recoverWith.applyOrElse(thrown, default)
        } else {
          val waitTime = delay(maxRetries, (maxRetries + 1) - n)
          logger(s"Retry #${(maxRetries + 1) - n} of $maxRetries delaying milliseconds=${waitTime.toMillis}")
          after(waitTime, scheduler)(tryCall(n - 1))
        }
      }
    }
    tryCall(maxRetries)
  }

  def apply[T, U] = applyOne(_: T => Future[U], PartialFunction.empty)
  def apply[T, U](recoverWith: PartialFunction[Throwable, Future[U]]) = applyOne(_: T => Future[U], recoverWith)

  def apply[T, U](f: T => Future[U]): T => Future[U] = { t: T =>
    applyOne(f, PartialFunction.empty)(t)
  }
}
