package com.nike.fleam
package sqs

import java.util

import akka.stream.scaladsl._
import com.amazonaws.services.sqs.model._
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
    val response = new ReceiveMessageResult()
      .withMessages(
        new Message().withBody("Message 1"),
        new Message().withBody("Message 2")
      )

    val fetcher: SqsSource.Fetch = (request: ReceiveMessageRequest) => {
      val expected = new ReceiveMessageRequest()
        .withQueueUrl(url)
        .withMaxNumberOfMessages(10)
        .withMessageAttributeNames("All")
        .withAttributeNames("All")
        .withWaitTimeSeconds(0)
      request should be(expected)
      Future.successful(response)
    }

    val source = new SqsSource(fetcher).forQueue(url).take(2)

    val result: Future[Seq[String]] = source.map(_.getBody).runWith(Sink.seq)

    whenReady(result) { _.toSet should contain theSameElementsAs Set("Message 1", "Message 2") }
  }

  it should "request attributes" in {
    val url = "http://test/queue"
    val response = new ReceiveMessageResult()
      .withMessages(
        new Message().withBody("Message 1").withAttributes(Map("foo" -> "bar").asJava),
        new Message().withBody("Message 2").withAttributes(Map("foo" -> "baz").asJava)
      )

    val fetcher: SqsSource.Fetch = (request: ReceiveMessageRequest) => {
      val expected = new ReceiveMessageRequest()
        .withQueueUrl(url)
        .withMaxNumberOfMessages(10)
        .withAttributeNames("foo")
        .withMessageAttributeNames("All")
        .withWaitTimeSeconds(0)
      request should be(expected)
      Future.successful(response)
    }

    val source = new SqsSource(fetcher).forQueue(url, attributeNames = Set("foo")).take(2)

    val result: Future[Seq[util.Map[String, String]]] = source.map(_.getAttributes).runWith(Sink.seq)

    val expected = Set(
      Map("foo" -> "bar"),
      Map("foo" -> "baz")
    )
    whenReady(result) { _.map(_.asScala.toMap) should contain theSameElementsAs expected}
  }

  it should "be killable" in {
    val url = "http://test/queue"
    val response = new ReceiveMessageResult()
      .withMessages(
        new Message().withBody("Message 1"),
        new Message().withBody("Message 2")
      )

    val fetcher: SqsSource.Fetch = (request: ReceiveMessageRequest) => {
      Future.successful(response)
    }

    val source = new SqsSource(fetcher).forQueue(url)

    val (killSwitch, result) = source.map(_.getBody).toMat(Sink.seq)(Keep.both).run()
    killSwitch.shutdown()
    whenReady(result) { _ shouldBe a[Seq[_]] }

  }
}
