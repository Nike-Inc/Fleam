package com.nike.fleam
package sqs

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues
import software.amazon.awssdk.services.sqs.model.{Message, MessageAttributeValue}
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

    val message = Message.builder().build().setCount(1)

    message.messageAttributes.asScala should contain theSameElementsAs {
      Map("retryCount" -> MessageAttributeValue.builder().dataType("Number").stringValue("1").build())
      Map("retryCount" -> MessageAttributeValue.builder().dataType("Number").stringValue("1").build())
    }
  }

  it should "get a count in the correct message attribute" in {

    implicit val containsCount = MessageAttributes.count("retryCount")

    val message = Message.builder()
      .messageAttributes(
        Map("retryCount" -> MessageAttributeValue.builder().dataType("Number").stringValue("1").build()).asJava
      )
      .build()

    message.getCount shouldBe Right(1)
  }

  it should "provide a zero value if the attribute is missing" in {

    implicit val containsCount = MessageAttributes.count("retryCount")

    val message = Message.builder.build()

    message.getCount shouldBe Right(0)
  }

  it should "fail on an unparsable number" in {

    implicit val containsCount = MessageAttributes.count("retryCount")

    val message = Message.builder()
      .messageAttributes(
        Map("retryCount" -> MessageAttributeValue.builder().dataType("Number").stringValue("1sd").build()).asJava
      )
      .build()

    message.getCount.left.value shouldBe NumberFormatError("1sd", message)
  }

  it should "fail on a missing value" in {

    implicit val containsCount = MessageAttributes.count("retryCount")

    val message = Message.builder()
      .messageAttributes(
        Map("retryCount" -> MessageAttributeValue.builder().dataType("Number").build).asJava
      )
      .build()

    message.getCount.left.value shouldBe NullValueError(MessageAttributeValue.builder().dataType("Number").build(), message)
  }

  it should "not lose other message attributes" in {

    implicit val containsCount = MessageAttributes.count("retryCount")

    val message = Message.builder()
      .messageAttributes(
        Map("someOtherThing" -> MessageAttributeValue.builder().dataType("Number").stringValue("1").build()).asJava
      )
        .build()

    message.setCount(3).messageAttributes.asScala should contain theSameElementsAs {
        Map(
          "someOtherThing" -> MessageAttributeValue.builder().dataType("Number").stringValue("1").build(),
          "retryCount" -> MessageAttributeValue.builder().dataType("Number").stringValue("3").build()
        )
    }
  }
}
