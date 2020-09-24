package com.nike.fleam

import akka.actor.ActorSystem
import akka.stream.ThrottleMode
import akka.stream.scaladsl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.Promise
import scala.concurrent.duration._

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
    val flowProccessed = Promise[Int]

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

    daemon.stop().onComplete  { case _ =>  actorSystem.terminate }

    whenReady(sum) { _ should not be(0) }
  }
}
