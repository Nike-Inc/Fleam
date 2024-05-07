package com.nike.fleam.sqs
package ops

import org.apache.pekko.stream._
import org.apache.pekko.stream.scaladsl._
import software.amazon.awssdk.services.sqs.model.Message
import java.time.Instant
import scala.language.implicitConversions

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

trait TimestampedMessageOps {
  implicit def timestampedMessageSourceOps[Mat](source: Graph[SourceShape[Message], Mat]): TimestampedMessageSource[Mat] =
    new TimestampedMessageSource[Mat](source)

  implicit def timestampedMessageFlowOps[In, Mat](flow: Graph[FlowShape[In, Message], Mat]): TimestampedMessageFlow[In, Mat] =
    new TimestampedMessageFlow[In, Mat](flow)
}

class TimestampedMessageSource[Mat](val source: Graph[SourceShape[Message], Mat]) extends AnyVal {
  def timestampMessage(now: () => Instant = () => Instant.now): Source[RetrievedMessage, Mat] =
    Source.fromGraph(source).map(message => RetrievedMessage(message, now()))
}

class TimestampedMessageFlow[In, Mat](val flow: Graph[FlowShape[In, Message], Mat]) extends AnyVal {
  def timestampMessage(now: () => Instant = () => Instant.now): Flow[In, RetrievedMessage, Mat] =
    Flow.fromGraph(flow).map(message => RetrievedMessage(message, now()))
}
