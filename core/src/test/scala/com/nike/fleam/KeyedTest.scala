package com.nike.fleam

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class KeyedTest extends AnyFlatSpec with Matchers {

  import ops.all._

  case class Example(key: String)

  implicit val keyedExample = Keyed.lift[Example, String](_.key)

  it should "let you use the ops syntax" in {
    val example = Example("persistence")

    example.getKey shouldBe "persistence"
  }
}
