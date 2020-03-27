package com.nike.fleam
package sqs

import akka.stream.scaladsl._
import configuration._
import com.nike.fleam.configuration._
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import com.amazonaws.services.sqs.model._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues
import scala.jdk.CollectionConverters._
import cats.Semigroup
import cats.implicits._
import com.nike.fleam.sqs.instances.MissingMessageGroupId
import implicits._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class SqsReduceTest extends AnyFlatSpec with Matchers with ScalaFutures with EitherValues {
  behavior of "SqsReduce"

  import TestTools._

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 10.millis)

  val config = SqsReduceConfiguration(
    queue = new SqsQueueConfiguration(url = "http://testurl"),
    grouping = new  GroupedWithinConfiguration(batchSize = 10, within = 1.seconds),
    deleteParallelism = 1)

  implicit val messageSemigroup = new Semigroup[Message] {
    def combine(left: Message, right: Message): Message = {
      left
    }
  }

  it should "reduce items into a single message and delete the originals" in {

    val reEnqueued = Promise[Message]
    val deleted = Promise[List[Message]]

    val reduce = new SqsReduce(
      config = config,
      reEnqueueMessages = { message =>
        reEnqueued.success(message)

        val result = new SendMessageResult()
          .withMessageId(message.getMessageId)

        Future.successful(result)
      },
      deleteMessages = { messages =>
        deleted.success(messages)
        val results = messages.map { message =>
          message -> new DeleteMessageBatchResultEntry()
            .withId(message.getMessageId)
        }.toMap

        Future.successful(BatchResult(
          deleteMessageBatchResult = new DeleteMessageBatchResult().withSuccessful(results.values.toList.asJava),
          failed = Nil,
          successful = results.map { case (message, entry) => SuccessfulResult(message, entry) }.toList
        ))
      })

      val messages = for { i <- 1 to 10 } yield {
        new Message()
          .withMessageId(i.toString)
          .withAttributes(Map("MessageGroupId" -> "test").asJava)
      }

      val output = Source(messages)
        .via(reduce.flow[Message, String])
        .runWith(Sink.seq)

      reEnqueued.future.futureValue shouldBe {
        new Message()
          .withMessageId("1")
          .withAttributes(Map("MessageGroupId" -> "test").asJava)
      }
      deleted.future.futureValue should contain theSameElementsAs messages
      output.futureValue should contain theSameElementsAs {
        messages.map { message =>
          Reduced(message, batchOf = 10, new SendMessageResult().withMessageId("1")).asLeft
        }
      }
  }

  it should "marked failed deletes as such" in {

    val reEnqueued = Promise[Message]
    val deleted = Promise[List[Message]]

    val reduce = new SqsReduce(
      config = config,
      reEnqueueMessages = { message =>
        reEnqueued.success(message)

        val result = new SendMessageResult()
          .withMessageId(message.getMessageId)

        Future.successful(result)
      },
      deleteMessages = { messages =>
        deleted.success(messages)
        val results = messages.map { message =>
          message -> new BatchResultErrorEntry()
            .withId(message.getMessageId)
        }.toMap

        Future.successful(BatchResult(
          deleteMessageBatchResult = new DeleteMessageBatchResult().withFailed(results.values.toList.asJava),
          failed = results.map { case (message, entry) => FailedResult(message, entry) }.toList,
          successful = Nil
        ))
      })

    val messages = for { i <- 1 to 10 } yield {
      new Message()
        .withMessageId(i.toString)
        .withAttributes(Map("MessageGroupId" -> "test").asJava)
    }

    val output = Source(messages)
      .via(reduce.flow[Message, String])
      .runWith(Sink.seq)

    reEnqueued.future.futureValue shouldBe {
      new Message()
        .withMessageId("1")
        .withAttributes(Map("MessageGroupId" -> "test").asJava)
    }
    deleted.future.futureValue should contain theSameElementsAs messages
    output.futureValue should contain theSameElementsAs {
      messages.map { message =>
        FailedDelete(FailedResult(message, new BatchResultErrorEntry().withId(message.getMessageId)), message).asLeft
      }
    }
  }

  it should "marked failed enqueues as such" in {

    val reEnqueued = Promise[Message]

    val exception = new InvalidMessageContentsException("asdf")

    val reduce = new SqsReduce(
      config = config,
      reEnqueueMessages = { message =>
        reEnqueued.success(message)
        Future.failed(exception)
      },
      deleteMessages = { messages => throw new Exception("Unpexected call to delete messages") }
    )

    val messages = for { i <- 1 to 10 } yield {
      new Message()
        .withMessageId(i.toString)
        .withAttributes(Map("MessageGroupId" -> "test").asJava)
    }

    val output = Source(messages)
      .via(reduce.flow[Message, String])
      .runWith(Sink.seq)

    reEnqueued.future.futureValue shouldBe {
      new Message()
        .withMessageId("1")
        .withAttributes(Map("MessageGroupId" -> "test").asJava)
    }
    output.futureValue should contain theSameElementsAs {
      val combinedMessge = new Message()
        .withMessageId("1")
        .withAttributes(Map("MessageGroupId" -> "test").asJava)

      messages.map { message =>
        EnqueueError(exception, message, combinedMessge).asLeft
      }
    }
  }

  it should "marked items with bad keys as such" in {

    val reduce = new SqsReduce(
      config = config,
      reEnqueueMessages = { message =>
        throw new Exception("Unpexected call to enqueue messages")
      },
      deleteMessages = { messages => throw new Exception("Unpexected call to delete messages") }
    )

    val messages = for { i <- 1 to 10 } yield {
      new Message()
        .withMessageId(i.toString)
        // No group Id
    }

    val output = Source(messages)
      .via(reduce.flow[Message, String])
      .runWith(Sink.seq)

    output.futureValue should contain theSameElementsAs {
      messages.map { message =>
        BadKey(MissingMessageGroupId(message), message).asLeft
      }
    }
  }
}
