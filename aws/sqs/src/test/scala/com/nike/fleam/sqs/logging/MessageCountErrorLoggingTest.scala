package com.nike.fleam.sqs
package logging

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.sqs.model.{Message, MessageAttributeValue}
import cats.implicits._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class MessageCountErrorLoggingTest extends AnyFlatSpec with Matchers {
  implicit val errorShow = new MessageCountErrorLogging().messageCountErrorShow

  it should "Log a failure to parse a count" in {
    (NumberFormatError("number-1", Message.builder.build()) : MessageCountError).show shouldBe
    "Unable to parse retry value as integer. Tried parsing 'number-1'"
  }

  it should "Log a failure parse a null value" in {
    val attribute = MessageAttributeValue.builder().dataType("Binary").build()
    (NullValueError(attribute, Message.builder.build()) : MessageCountError).show shouldBe
    "Retry value not a string value. MessageAttributeValue(DataType=Binary)"
  }
}
