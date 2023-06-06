package com.nike.fleam

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.Id

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

case class Example(i: Int)

class ContainsCountTest extends AnyFlatSpec with Matchers {

  import ops.all._

  implicit val exampleContainsCount: ContainsCount[Example, Id] = new ContainsCount[Example, Id] {
    def getCount(example: Example): Id[Int] = example.i
    def setCount(example: Example)(i: Int): Example = Example(i)
  }

  it should "let you use the ops syntax" in {
    val example = Example(1)

    example.getCount shouldBe 1
    example.setCount(2) shouldBe Example(2)
  }
}
