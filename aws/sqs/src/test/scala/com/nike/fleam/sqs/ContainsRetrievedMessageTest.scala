package com.nike.fleam.sqs

import software.amazon.awssdk.services.sqs.model._
import java.time.Instant
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import implicits._
import cats.implicits._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class ContainsRetrievedMessageTest extends AnyFlatSpec with Matchers with ScalaFutures with OptionValues {
  it should "derive a message holder and retrieved time from a ContainsRetrievedMessage" in {
    val now = Instant.now
    val retrievedMessage = RetrievedMessage(message = Message.builder().build(), timestamp = now)
    case class Example(retrieved: RetrievedMessage)

    implicit val containsRetrievedMessageExample: ContainsRetrievedMessage[Example] =
      ContainsRetrievedMessage.lift[Example](_.retrieved)

    val example = Example(retrievedMessage)

    example.getMessage should be(Message.builder().build())
    example.getRetrievedTime should be(now)
  }

  it should "derive a message holder and retrieved time from a ContainsRetrievedMessage for sub-types" in {
    val now = Instant.now
    val retrievedMessage = RetrievedMessage(message = Message.builder().build(), timestamp = now)

    sealed trait Example
    case class ExampleFoo(retrieved: RetrievedMessage) extends Example
    case class ExampleBar(message: RetrievedMessage) extends Example

    implicit val containsRetrievedMessageExample: ContainsRetrievedMessage[Example] =
      ContainsRetrievedMessage.lift[Example] {
      case ExampleFoo(r) => r
      case ExampleBar(m) => m
    }

    val exampleFoo = ExampleFoo(retrievedMessage)

    exampleFoo.getMessage should be(Message.builder().build())
    exampleFoo.getRetrievedTime should be(now)
    exampleFoo.getRetrievedMessage should be(retrievedMessage)

    val exampleBar = ExampleBar(retrievedMessage)

    exampleBar.getMessage should be(Message.builder().build())
    exampleBar.getRetrievedTime should be(now)
    exampleBar.getRetrievedMessage should be(retrievedMessage)
  }

  it should "derive an Either instance for ContainsRetrievedMessage" in {
    case class A(retrieved: RetrievedMessage)
    case class B(retrieved: RetrievedMessage)

    implicit val aContainsRetrievedMessage: ContainsRetrievedMessage[A] = ContainsRetrievedMessage.lift(_.retrieved)
    implicit val bContainsRetrievedMessage: ContainsRetrievedMessage[B] = ContainsRetrievedMessage.lift(_.retrieved)

    val retrievedMessage = RetrievedMessage(message = Message.builder().build(), timestamp = Instant.now)

    val a = A(retrievedMessage)
    val b = B(retrievedMessage)

    val left = a.asLeft[B]
    val right = b.asRight[A]

    left.getRetrievedMessage shouldBe retrievedMessage
    right.getRetrievedMessage shouldBe retrievedMessage
  }
}
