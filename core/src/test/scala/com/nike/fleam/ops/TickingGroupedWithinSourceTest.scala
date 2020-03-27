package com.nike.fleam
package ops

import akka.stream.scaladsl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.Future
import scala.concurrent.duration._
import com.nike.fleam.implicits._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class TickingGroupedWithinSourceTest extends AnyFlatSpec with Matchers with ScalaFutures {

  import TestTools._

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 20.seconds, interval = 10.millis)

  it should "tick groups even when no values are passing from a source" in {
    val result = Source.fromFuture(Future { Thread.sleep(2000); 1 })
      .tickingGroupedWithin(batchSize = 100, within = 100.millis)
      .take(10)
      .runWith(Sink.seq)

    whenReady(result) { groups =>
      groups.length should be(10)
      assert(groups.forall(_ == Seq.empty))
    }
  }
}
