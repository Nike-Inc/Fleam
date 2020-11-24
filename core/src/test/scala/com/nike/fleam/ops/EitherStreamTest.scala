package com.nike.fleam
package ops

import akka.stream.scaladsl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import cats.implicits._
import scala.concurrent.Future
import scala.concurrent.duration._
import implicits._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class EitherStreamTest extends AnyFlatSpec with Matchers with ScalaFutures {

  import TestTools._
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 20.seconds, interval = 10.millis)

  it should "joinRight" in {

    val flow = Flow[Either[Int, Either[Int, String]]]
      .joinRight

    Source.single(Left(1)).via(flow).runWith(Sink.head).futureValue should be (Left(1))
  }

  it should "joinRight with convert" in {

    val flow = Flow[Either[Double, Either[Int, String]]]
      .joinRight(d => d.toInt)

    Source.single(Left(1.0)).via(flow).runWith(Sink.head).futureValue should be (Left(1))
  }

  it should "joinLeft" in {

    val flow = Flow[Either[Either[Int, String], String]]
      .joinLeft

    Source.single(Right("asdf")).via(flow).runWith(Sink.head).futureValue should be (Right("asdf"))
  }

  it should "joinLeft with convert" in {

    val flow = Flow[Either[Either[Double, Int], String]]
      .joinLeft(s => s.toInt)

    Source.single(Right("1")).via(flow).runWith(Sink.head).futureValue should be (Right(1))
  }

  it should "allow a flow to map over an either" in {
    val flow = Flow[Either[String, Int]]
      .eitherMap(_ * 2)

    Source.single(Right(1)).via(flow).runWith(Sink.head).futureValue should be (Right(2))
  }

  it should "allow a source to map over an either" in {
    val source = Source.single(1.asRight[String])
      .eitherMap(_ * 2)

    source.runWith(Sink.head).futureValue should be (Right(2))
  }

  it should "allow a flow map over an either with a future function" in {
    val flow = Flow[Either[String, Int]]
      .eitherMapAsync(1)(i => Future.successful(i * 2))

    Source.single(Right(1)).via(flow).runWith(Sink.head).futureValue should be (Right(2))
  }

  it should "allow a source map over an either with a future function" in {
    val source = Source.single(1.asRight[String])
      .eitherMapAsync(1)(i => Future.successful(i * 2))

    source.runWith(Sink.head).futureValue should be (Right(2))
  }

  it should "allow a flow map over an either with a future function unordered" in {
    val flow = Flow[Either[String, Int]]
      .eitherMapAsyncUnordered(1)(i => Future.successful(i * 2))

    Source.single(Right(1)).via(flow).runWith(Sink.head).futureValue should be (Right(2))
  }

  it should "allow a source map over an either with a future function unordered" in {
    val source = Source.single(1.asRight[String])
      .eitherMapAsyncUnordered(1)(i => Future.successful(i * 2))

    source.runWith(Sink.head).futureValue should be (Right(2))
  }

  it should "map over left values" in {
    val source = Source.single(1.asLeft[String])
      .eitherLeftMap(_ * 2)

    source.runWith(Sink.head).futureValue should be (Left(2))
  }

  it should "dropLeft" in {
    val source = Source(List[Either[Int, String]](1.asLeft, "two".asRight, "three".asRight))
      .dropLeft()

    source.runWith(Sink.seq).futureValue should be (Seq("two","three"))
  }

  it should "dropRight" in {
    val source = Source(List[Either[Int, String]](1.asLeft, "two".asRight, "three".asRight))
      .dropRight()

    source.runWith(Sink.seq).futureValue should be (Seq(1))
  }
}
