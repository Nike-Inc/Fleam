package com.nike.fleam
package ops

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.Future
import com.nike.fleam.implicits._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class FutureFunctionHelperTest extends AnyFlatSpec with Matchers with ScalaFutures {

  import TestTools._

  it should "turn a function one into a stream" in {
    val f: Int => Future[Int] = { i => Future.successful(i * 2) }

    val ints = 1 to 50

    val result = f.toStream(parallelism = 2)(ints)

    result.futureValue should be (2 to 100 by 2)
  }

  it should "turn a function two into a stream" in {
    val f: (Int, Int) => Future[Int] = { (i1, i2) => Future.successful(i1 + i2) }

    val ints = (1 to 50).zip(1 to 50)

    val result = f.toStream(parallelism = 2)(ints)

    result.futureValue should be (2 to 100 by 2)
  }
}
