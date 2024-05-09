package com.nike.fleam
package sqs

import software.amazon.awssdk.services.sqs.model.Message
import org.apache.pekko.stream.scaladsl.{Flow, Source, Sink}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import java.time.Instant
import implicits._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class TimestampedMessageTest extends AnyFlatSpec with Matchers with ScalaFutures {
  import TestTools._

  behavior of "TimestampedMessage"

  it should "time stamp a message in a flow" in {
    val now = Instant.ofEpochMilli(1599778039241L)
    val flow = Flow[Message].timestampMessage(() => now)

    val message = Message.builder.build()
    val result =  Source.single(message).via(flow).runWith(Sink.head)

    result.futureValue shouldBe { RetrievedMessage(message, now) }
  }

  it should "time stamp a message in a source" in {
    val now = Instant.ofEpochMilli(1599778039241L)

    val message = Message.builder.build()
    val result =  Source.single(message).timestampMessage(() => now).runWith(Sink.head)

    result.futureValue shouldBe { RetrievedMessage(message, now) }
  }
}
