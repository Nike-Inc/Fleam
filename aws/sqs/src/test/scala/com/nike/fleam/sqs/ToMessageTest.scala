package com.nike.fleam.sqs

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.implicits._
import com.nike.fleam.sqs.implicits._
import software.amazon.awssdk.services.sqs.model.Message

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class ToMessageTest extends AnyFlatSpec with Matchers {

  behavior of "ToMessage"

  it should "derive an Either instance" in {
    case class A(s: String)
    case class B(s: String)

    implicit val aToMessage: ToMessage[A] = ToMessage.lift { a =>
      Message.builder().body(a.s).build()
    }

    implicit val bToMessage: ToMessage[B] = ToMessage.lift { b =>
      Message.builder().body(b.s).build()
    }

    val a = A("foo")
    val b = B("bar")

    val left = a.asLeft[B]
    val right = b.asRight[A]

    left.toMessage shouldBe Message.builder().body("foo").build()
    right.toMessage shouldBe Message.builder().body("bar").build()
  }
}
