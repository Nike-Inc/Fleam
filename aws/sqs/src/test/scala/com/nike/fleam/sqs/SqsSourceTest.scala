package com.nike.fleam
package sqs

import java.util

import org.apache.pekko.stream.scaladsl._
import software.amazon.awssdk.services.sqs.model._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.jdk.CollectionConverters._
import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.concurrent.duration._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class SqsSourceTest extends AnyFlatSpec with Matchers with ScalaFutures {

  behavior of "SqsSource"

  import TestTools._

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 10.seconds, interval = 10.millis)

  it should "make a request to Sqs" in {
    val url = "http://test/queue"
    val response = ReceiveMessageResponse.builder()
      .messages(
        Message.builder().body("Message 1").build(),
        Message.builder().body("Message 2").build()
      )
      .build()

    val fetcher: SqsSource.Fetch = (request: ReceiveMessageRequest) => {
      val expected = ReceiveMessageRequest.builder()
        .queueUrl(url)
        .maxNumberOfMessages(10)
        .messageAttributeNames("All")
        .messageSystemAttributeNames(MessageSystemAttributeName.ALL)
        .waitTimeSeconds(0)
        .build()

      request should be(expected)
      Future.successful(response)
    }

    val source = new SqsSource(fetcher).forQueue(url).take(2)

    val result: Future[Seq[String]] = source.map(_.body).runWith(Sink.seq)

    whenReady(result) { _.toSet should contain theSameElementsAs Set("Message 1", "Message 2") }
  }

  it should "request attributes" in {
    val url = "http://test/queue"
    val response = ReceiveMessageResponse.builder()
      .messages(
        Message.builder()
          .body("Message 1")
          .attributes(Map(MessageSystemAttributeName.SENDER_ID -> "bar").asJava).build(),
        Message.builder()
          .body("Message 2")
          .attributes(Map(MessageSystemAttributeName.SENDER_ID -> "baz").asJava).build()
      )
      .build()

    val fetcher: SqsSource.Fetch = (request: ReceiveMessageRequest) => {
      val expected = ReceiveMessageRequest.builder()
        .queueUrl(url)
        .maxNumberOfMessages(10)
        .messageSystemAttributeNames(MessageSystemAttributeName.ALL)
        .messageAttributeNames("All")
        .waitTimeSeconds(0)
        .build()

      request should be(expected)
      Future.successful(response)
    }

    val source = new SqsSource(fetcher).forQueue(url, attributeNames = Set("All")).take(2)

    val result: Future[Seq[util.Map[MessageSystemAttributeName, String]]] = source.map(_.attributes).runWith(Sink.seq)

    val expected = Set(
      Map(MessageSystemAttributeName.SENDER_ID -> "bar"),
      Map(MessageSystemAttributeName.SENDER_ID -> "baz")
    )
    whenReady(result) { _.map(_.asScala.toMap) should contain theSameElementsAs expected}
  }

  it should "be killable" in {
    val url = "http://test/queue"
    val response = ReceiveMessageResponse.builder()
      .messages(
        Message.builder().body("Message 1").build(),
        Message.builder().body("Message 2").build()
      )
      .build()

    val fetcher: SqsSource.Fetch = (request: ReceiveMessageRequest) => {
      Future.successful(response)
    }

    val source = new SqsSource(fetcher).forQueue(url)

    val (killSwitch, result) = source.map(_.body).toMat(Sink.seq)(Keep.both).run()
    killSwitch.shutdown()
    whenReady(result) { _ shouldBe a[Seq[_]] }

  }
}
