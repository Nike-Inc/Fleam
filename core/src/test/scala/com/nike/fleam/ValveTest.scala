package com.nike.fleam

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}

import scala.concurrent.duration._
import cats.~>
import cats.implicits._

import scala.concurrent.{Future, Promise}
import scala.util.Failure

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class ValveTest extends AnyFlatSpec with Matchers with ScalaFutures with IntegrationPatience {

  behavior of "Valve"

  import TestTools.actorSystem
  import actorSystem.dispatcher

  it should "provide an exponential backoff delay" in {
    val f = Valve.exponentialBackoff(2.seconds)
    val maxRetries = 3

    f(maxRetries, 1) should be(2.seconds)
    f(maxRetries, 2) should be(4.seconds)
    f(maxRetries, 3) should be(8.seconds)
  }

  it should "provide a constant delay" in {
    val f = Valve.constant(2.seconds)
    val maxRetries = 3

    f(maxRetries, 1) should be(2.seconds)
    f(maxRetries, 2) should be(2.seconds)
    f(maxRetries, 3) should be(2.seconds)
  }

  it should "provide a multipled delay" in {
    val f = Valve.multipled(2.seconds)
    val maxRetries = 3

    f(maxRetries, 1) should be(2.seconds)
    f(maxRetries, 2) should be(4.seconds)
    f(maxRetries, 3) should be(6.seconds)
  }

  it should "call the delay function in ascending order" in {
    var retries = List.empty[Int]
    val circuitBreaker = new (Future ~> Future) {
      def apply[T](f: Future[T]): Future[T] = {
          Future.failed(new Throwable)
      }
    }

    val delay: Valve.DelayCalculation = (maxRetries, currentRetry) => {
      retries = retries :+ currentRetry
      1.milliseconds
    }

    val valve = new Valve(circuitBreaker, 4, delay)

    val call = valve({ _: Int => Future.successful(1)})
    whenReady(call(1).failed) { _ =>
      retries should be(List(1,2,3,4))
    }
  }

  it should "keep trying the same element when the circuit breaker is open" in {
    var failures = 3
    var tries = 0
    val circuitBreaker = new (Future ~> Future) {
      def apply[T](f: Future[T]): Future[T] = {
        if (failures > 0) {
          failures -= 1
          Future.failed(new Throwable)
        } else {
          f
        }
      }
    }

    val f = (_: String) => { tries += 1; Future.successful("asdf!") }

    val valve = new Valve(circuitBreaker, maxRetries = 6, delay = Valve.constant(10.millis))

    val boundary = valve(f)

    whenReady(boundary("test")) { result =>
      result should be("asdf!")
      tries should be(4)
    }
  }

  it should "give-up after maxRetries when the circuit breaker is open" in {
    var failures = 3
    var tries = 0
    val thrown = new Throwable
    val circuitBreaker = new (Future ~> Future) {
      def apply[T](f: Future[T]): Future[T] = {
        if (failures > 0) {
          failures -= 1
          Future.failed(thrown)
        } else {
          f
        }
      }
    }

    val f = (_: String) => { tries += 1; Future.successful("asdf!") }

    val valve = new Valve(circuitBreaker, maxRetries = 2, delay = Valve.constant(10.millis))

    val boundary = valve(f)

    whenReady(boundary("test").failed) { exception =>
      exception should be(thrown)
      tries should be(3)
    }
  }

  it should "log attempts" in {
    var failures = 3
    var tries = 0
    var log = List.empty[String]
    val logger = (s: String) => { log = log :+ s; () }
    val circuitBreaker = new (Future ~> Future) {
      def apply[T](f: Future[T]): Future[T] = {
        if (failures > 0) {
          failures -= 1
          Future.failed(new Throwable)
        } else {
          f
        }
      }
    }

    val f = (_: String) => { tries += 1; Future.successful("asdf!") }

    val valve = new Valve(circuitBreaker, maxRetries = 6, delay = Valve.constant(10.millis), logger)

    val boundary = valve(f)

    whenReady(boundary("test")) { result =>
      log should contain theSameElementsAs List(
        "Retry #1 of 6 delaying milliseconds=10",
        "Retry #2 of 6 delaying milliseconds=10",
        "Retry #3 of 6 delaying milliseconds=10")
    }
  }

  it should "lift a value into an exception" in {
    val f: (String, String) => Future[Either[Int, String]] = (_, _) => Future.successful(Left(1))
    val exception = new Exception("asdf")
    val failed = Promise[Exception]
    val circuitBreaker = new (Future ~> Future) {
      def apply[T](f: Future[T]): Future[T] = f.andThen {
        case Failure(ex: Exception) => failed.success(ex)
      }
    }

    val valve = new Valve(circuitBreaker, maxRetries = 6, delay = Valve.constant(10.millis))
    val policy: PartialFunction[Either[Int, String], Exception] = { case Left(1) => exception }

    valve(Valve.liftFailedValues(policy)(f))("asdf!", "fdsa")

    whenReady(failed.future) { _ should be(exception) }
  }

  it should "let you turn an exception into another value while still trigger the circuit breaker" in {
    case class Error(i: Int) extends Throwable
    var tries = 0

    val circuitBreaker = new (Future ~> Future) {
      def apply[T](f: Future[T]): Future[T] = f
    }

    val f = (_: Either[Int, String]) => { tries += 1; Future.failed(new Error(1)) }

    val valve = new Valve(circuitBreaker, maxRetries = 3, delay = Valve.constant(10.millis))

    val recoverWith: PartialFunction[Throwable, Future[Either[Int, String]]] = {
      case Error(int) => Future.successful(int.asLeft)
    }

    val boundary = valve(recoverWith)(f)

    whenReady(boundary("test".asRight[Int])) { int =>
      int should be(1.asLeft)
      tries should be(4)
    }
  }
}
