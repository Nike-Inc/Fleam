package com.nike.fleam
package sqs

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues
import com.amazonaws.services.sqs.model.{Message, MessageAttributeValue}
import scala.jdk.CollectionConverters._
import ContainsCount.ops._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class MessageAttributesTest extends AnyFlatSpec with Matchers with EitherValues {

  it should "save a count in the correct message attribute" in {

    implicit val containsCount = MessageAttributes.count("retryCount")

    val message = new Message().setCount(1)

    message.getMessageAttributes.asScala should contain theSameElementsAs {
      Map("retryCount" -> new MessageAttributeValue().withDataType("Number").withStringValue("1"))
    }
  }

  it should "get a count in the correct message attribute" in {

    implicit val containsCount = MessageAttributes.count("retryCount")

    val message = new Message().withMessageAttributes(
      Map("retryCount" -> new MessageAttributeValue().withDataType("Number").withStringValue("1")).asJava
    )

    message.getCount shouldBe Right(1)
  }

  it should "provide a zero value if the attribute is missing" in {

    implicit val containsCount = MessageAttributes.count("retryCount")

    val message = new Message()

    message.getCount shouldBe Right(0)
  }

  it should "fail on an unparsable number" in {

    implicit val containsCount = MessageAttributes.count("retryCount")

    val message = new Message().withMessageAttributes(
      Map("retryCount" -> new MessageAttributeValue().withDataType("Number").withStringValue("1sd")).asJava
    )

    message.getCount.left.value shouldBe NumberFormatError("1sd", message)
  }

  it should "fail on a missing value" in {

    implicit val containsCount = MessageAttributes.count("retryCount")

    val message = new Message().withMessageAttributes(
      Map("retryCount" -> new MessageAttributeValue().withDataType("Number")).asJava
    )

    message.getCount.left.value shouldBe NullValueError(new MessageAttributeValue().withDataType("Number"), message)
  }

  it should "not lose other message attributes" in {

    implicit val containsCount = MessageAttributes.count("retryCount")

    val message = new Message().withMessageAttributes(
      Map("someOtherThing" -> new MessageAttributeValue().withDataType("Number").withStringValue("1")).asJava
    )

    message.setCount(3).getMessageAttributes.asScala should contain theSameElementsAs {
        Map(
          "someOtherThing" -> new MessageAttributeValue().withDataType("Number").withStringValue("1"),
          "retryCount" -> new MessageAttributeValue().withDataType("Number").withStringValue("3")
        )
    }
  }
}
