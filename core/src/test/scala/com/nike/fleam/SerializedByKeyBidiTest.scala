package com.nike.fleam

import akka.stream.scaladsl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import scala.util.Random
import scala.concurrent.Future
import scala.concurrent.duration._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class SerializedByKeyBidiTest extends AnyFlatSpec with Matchers with ScalaFutures {

  behavior of "SerializedByKeyBidi"
  import TestTools._

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 30.seconds, interval = 10.millis)

  it should "process a single item with no delay" in {
    // upstream finishing could come after the stage has no more work in this case
    case class Example(i: Int, delay: Int, key: Int)

    val example = Example(i = 1, delay = 0, key = 0)

    implicit val exampleKey = Keyed.lift[Example, Int](_.key)
    val bidi = SerializedByKeyBidi(
      bufferSize = 20,
      expiration = 10.seconds,
      expirationInterval = 5.seconds)

    val flow =
      Flow[Example]
        .mapAsyncUnordered(20) { example =>
          akka.pattern.after(example.delay.milliseconds, actorSystem.scheduler)(Future.successful(example))
        }

    val result = Source.single(example)
      .via(bidi.join(flow))
      .runWith(Sink.seq)

    result.futureValue shouldBe List(example)
  }

  it should "process items with the same key sequentially" in {
    case class Example(i: Int, delay: Int, key: Int)

    val keys = 10
    val items = 10
    val examples = for {
      key <- 1 to keys
      item <- 1 to items
    } yield { Example(i = item, delay = (Random.nextDouble() * 10).toInt, key = key) }

    implicit val exampleKey = Keyed.lift[Example, Int](_.key)
    val bidi = SerializedByKeyBidi(
      bufferSize = 20,
      expiration = 10.seconds,
      expirationInterval = 5.seconds)

    val flow =
      Flow[Example]
        .mapAsyncUnordered(20) { example =>
          akka.pattern.after(example.delay.milliseconds, actorSystem.scheduler)(Future.successful(example))
        }

    val result = Source(examples)
      .via(bidi.join(flow))
      .runWith(Sink.seq)

    // Not drop or gain elements
    result.futureValue.length shouldBe keys * items

    val sequentialByKeyProcessing = (for {
      key <- 1 to keys
    } yield {
      List.fill(items)(key)
    }).reduceLeft(_ ++ _)

    // Not processes keys sequentially
    result.futureValue.map(_.key) shouldNot be(sequentialByKeyProcessing)

    // Process items sequentially
    for {
      key <- 1 to keys
    } yield {
      result.futureValue.filter(example => example.key == key).map(_.i).toList shouldBe (1 to keys).toList
    }
  }

  it should "timeout items that don't come back into the bidi in time" in {
    case class Example(i: Int, delay: Int, key: Int, fail: Boolean)

    val examples = for {
      key <- 1 to 10
      item <- 1 to 10
    } yield {
      Example(i = item, delay = (Random.nextDouble() * 10).toInt, key = key, fail = item % 3 == 0)
    }

    implicit val exampleKey = Keyed.lift[Example, Int](_.key)
    val bidi = SerializedByKeyBidi(
      bufferSize = 20,
      expiration = 500.millis,
      expirationInterval = 250.millis
    )

    val flow =
      Flow[Example]
        .mapAsyncUnordered(10) { example =>
          akka.pattern.after(example.delay.milliseconds, actorSystem.scheduler)( Future {
            if (example.fail) throw new Exception() else example
          })
        }

    val result = Source(examples)
      .via(bidi.join(flow))
      .withAttributes(ResumeSupervisionStrategy)
      .runWith(Sink.seq)

    result.futureValue should contain theSameElementsAs(examples.filterNot(_.fail))
  }
}
