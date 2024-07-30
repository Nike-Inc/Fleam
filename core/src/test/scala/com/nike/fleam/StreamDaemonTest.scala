package com.nike.fleam

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.ThrottleMode
import org.apache.pekko.stream.scaladsl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.concurrent.Future

import java.util.concurrent.atomic.AtomicBoolean
import org.apache.pekko.stream.Supervision

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class StreamDaemonTest extends AnyFlatSpec with Matchers with ScalaFutures {

  behavior of "StreamDaemon"

  import TestTools.{ executionContext, materializer }

  it should "process a stream when started" in {
    val flowProccessed = Promise[Int]()

    val daemon = new StreamDaemon("test")

    daemon.start(
      source = Source.single(1),
      pipeline = Flow[Int].map(flowProccessed.success),
      sink = Sink.ignore)

    whenReady(flowProccessed.future) { _ shouldBe (1) }
  }

  it should "be able to stop a stream from processing" in {
    // Create separate actor system since we're going to stop it.
    implicit val actorSystem = ActorSystem("StreamTest", TestTools.config)
    implicit val executionContext = actorSystem.dispatcher
    implicit val patienceConfig = PatienceConfig(timeout = 10.seconds, interval = 10.millis)

    val daemon = new StreamDaemon("test")

    val sum = daemon.start(
      source = Source.repeat(1).throttle(1, 1.second, 1, ThrottleMode.Shaping),
      pipeline = Flow[Int],
      sink = Sink.fold[Int, Int](0)(_ + _))

    daemon.stop().onComplete  { case _ =>  actorSystem.terminate() }

    whenReady(sum) { _ should not be(0) }
  }

  it should "apply a supervision property to source, pipeline, and sink" in {
    val flowProccessed = Promise[String]()

    val sourceFn: Int => Future[Int] = failOnce(identity)
    val pipelineFn: Int => Future[String] = failOnce("*".repeat(_))
    val sinkFn: String => Future[String] = failOnce(s => s"result=$s")

    val daemon = new StreamDaemon("test")

    val supervisor: Supervision.Decider = {
      case t => System.err.println(t); Supervision.Resume
    }

    // Run several values to test failures at each stage
    // 1 fails during source function, 2 fails during pipeline function, and 3 fails during sink function
    // 4 is successful, converts to **** and then Sink fucntion turns into "result=****"
    daemon.start(
      source = Source(List(1, 2, 3, 4)).mapAsync(1)(sourceFn),
      pipeline = Flow[Int].mapAsync(1)(pipelineFn),
      sink = Sink.foreachAsync[String](1)(r => sinkFn(r).map(v => flowProccessed.success(v)).map(_ => ())),
      supervisionStrategy = supervisor)

    whenReady(flowProccessed.future) { _ shouldBe ("result=****") }
  }

  def failOnce[I, O](f: I => O): I => Future[O] = {
    val failed = new AtomicBoolean(false)

    (t) => {
      if (failed.compareAndSet(false, true)) {
        Future.failed(new Exception(s"Failed to provide $value"))
      } else {
        Future.successful(f(t))
      }
    }
  }
}
