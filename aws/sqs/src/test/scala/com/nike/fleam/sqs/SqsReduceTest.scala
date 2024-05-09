package com.nike.fleam
package sqs

import org.apache.pekko.stream.scaladsl._
import configuration._
import com.nike.fleam.configuration._
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import software.amazon.awssdk.services.sqs.model._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues
import scala.jdk.CollectionConverters._
import cats.Semigroup
import cats.implicits._
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
    grouping = new GroupedWithinConfiguration(batchSize = 10, within = 1.seconds),
    deleteParallelism = 1)

  implicit val messageSemigroup: Semigroup[Message] = new Semigroup[Message] {
    def combine(left: Message, right: Message): Message = {
      left
    }
  }

  it should "reduce items into a single message and delete the originals" in {

    val reEnqueued = Promise[Message]()
    val deleted = Promise[List[Message]]()

    val reduce = new SqsReduce(
      config = config,
      reEnqueueMessages = { message =>
        reEnqueued.success(message)

        val result = SendMessageResponse.builder()
          .messageId(message.messageId)
          .build()

        Future.successful(result)
      },
      deleteMessages = { messages =>
        deleted.success(messages)
        val results = messages.map { message =>
          message -> DeleteMessageBatchResultEntry.builder()
            .id(message.messageId)
            .build()
        }.toMap

        Future.successful(BatchResult(
          deleteMessageBatchResponse = DeleteMessageBatchResponse.builder().successful(results.values.toList.asJava).build(),
          failed = Nil,
          successful = results.map { case (message, entry) => SuccessfulResult(message, entry) }.toList
        ))
      })

      val messages = for { i <- 1 to 10 } yield {
        Message.builder()
          .messageId(i.toString)
          .attributes(Map(MessageSystemAttributeName.MESSAGE_GROUP_ID-> "test").asJava)
          .build()
      }

      val output = Source(messages)
        .via(reduce.flow[Message, MessageGroupId])
        .runWith(Sink.seq)

      reEnqueued.future.futureValue shouldBe {
        Message.builder()
          .messageId("1")
          .attributes(Map(MessageSystemAttributeName.MESSAGE_GROUP_ID -> "test").asJava)
          .build()
      }
      deleted.future.futureValue should contain theSameElementsAs messages
      output.futureValue should contain theSameElementsAs {
        messages.map { message =>
          Reduced(message, batchOf = 10, SendMessageResponse.builder().messageId("1").build()).asLeft
        }
      }
  }

  it should "marked failed deletes as such" in {

    val reEnqueued = Promise[Message]()
    val deleted = Promise[List[Message]]()

    val reduce = new SqsReduce(
      config = config,
      reEnqueueMessages = { message =>
        reEnqueued.success(message)

        val result = SendMessageResponse.builder()
          .messageId(message.messageId)
          .build()

        Future.successful(result)
      },
      deleteMessages = { messages =>
        deleted.success(messages)
        val results = messages.map { message =>
          message -> BatchResultErrorEntry.builder()
            .id(message.messageId)
            .build()
        }.toMap

        Future.successful(BatchResult(
          deleteMessageBatchResponse = DeleteMessageBatchResponse.builder().failed(results.values.toList.asJava).build(),
          failed = results.map { case (message, entry) => FailedResult(message, entry) }.toList,
          successful = Nil
        ))
      })

    val messages = for { i <- 1 to 10 } yield {
      Message.builder()
        .messageId(i.toString)
        .attributes(Map(MessageSystemAttributeName.MESSAGE_GROUP_ID -> "test").asJava)
        .build()
    }

    val output = Source(messages)
      .via(reduce.flow[Message, MessageGroupId])
      .runWith(Sink.seq)

    reEnqueued.future.futureValue shouldBe {
      Message.builder()
        .messageId("1")
        .attributes(Map(MessageSystemAttributeName.MESSAGE_GROUP_ID -> "test").asJava)
        .build()
    }
    deleted.future.futureValue should contain theSameElementsAs messages
    output.futureValue should contain theSameElementsAs {
      messages.map { message =>
        FailedDelete(FailedResult(message, BatchResultErrorEntry.builder().id(message.messageId).build()), message).asLeft
      }
    }
  }

  it should "marked failed enqueues as such" in {

    val reEnqueued = Promise[Message]()

    val exception = InvalidMessageContentsException.builder.message("asdf").build().asInstanceOf[SqsException]

    val reduce = new SqsReduce(
      config = config,
      reEnqueueMessages = { message =>
        reEnqueued.success(message)
        Future.failed(exception)
      },
      deleteMessages = { messages => throw new Exception("Unpexected call to delete messages") }
    )

    val messages = for { i <- 1 to 10 } yield {
      Message.builder()
        .messageId(i.toString)
        .attributes(Map(MessageSystemAttributeName.MESSAGE_GROUP_ID -> "test").asJava)
        .build()
    }

    val output = Source(messages)
      .via(reduce.flow[Message, MessageGroupId])
      .runWith(Sink.seq)

    reEnqueued.future.futureValue shouldBe {
      Message.builder()
        .messageId("1")
        .attributes(Map(MessageSystemAttributeName.MESSAGE_GROUP_ID -> "test").asJava)
        .build()
    }
    output.futureValue should contain theSameElementsAs {
      val combinedMessge = Message.builder()
        .messageId("1")
        .attributes(Map(MessageSystemAttributeName.MESSAGE_GROUP_ID -> "test").asJava)
        .build()

      messages.map { message =>
        EnqueueError(exception, message, combinedMessge).asLeft
      }
    }
  }
}
