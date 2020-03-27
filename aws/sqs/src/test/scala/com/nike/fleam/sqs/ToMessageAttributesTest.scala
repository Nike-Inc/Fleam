package com.nike.fleam.sqs

import com.amazonaws.services.sqs.model._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import implicits._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class ToMessageAttributesTest extends AnyFlatSpec with Matchers with ScalaFutures with OptionValues {
  behavior of "ToMessageAttributes"

  it should "turn a pair of strings into a Message Attribute Value Map" in {
    ("uh-oh's" -> "We uh-oh'd").toMessageAttributes shouldBe {
      Map(
        "uh-oh's" -> new MessageAttributeValue().withDataType("String").withStringValue("We uh-oh'd")
      )
    }
  }

  it should "turn a pair of (string, int) into a Message Attribute Value Map" in {
    ("uh-oh's" -> 1).toMessageAttributes shouldBe {
      Map(
        "uh-oh's" -> new MessageAttributeValue().withDataType("Number").withStringValue("1")
      )
    }
  }

  it should "turn a pair of (string, double) into a Message Attribute Value Map" in {
    ("uh-oh's" -> 1.2).toMessageAttributes shouldBe {
      Map(
        "uh-oh's" -> new MessageAttributeValue().withDataType("Number").withStringValue("1.2")
      )
    }
  }

  it should "turn a pair of (string, long) into a Message Attribute Value Map" in {
    ("uh-oh's" -> 100000000L).toMessageAttributes shouldBe {
      Map(
        "uh-oh's" -> new MessageAttributeValue().withDataType("Number").withStringValue("100000000")
      )
    }
  }

  it should "turn a Map[String, T: ToMessageAttributes] into a Message Attribute Value Map" in {
    Map(
      "error 1" -> "reason 1",
      "error 2" -> "reason 2"
    ).toMessageAttributes shouldBe {
      Map(
        "error 1" -> new MessageAttributeValue().withDataType("String").withStringValue("reason 1"),
        "error 2" -> new MessageAttributeValue().withDataType("String").withStringValue("reason 2")
      )
    }
  }
}
