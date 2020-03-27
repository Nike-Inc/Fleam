package com.nike.fleam.sqs
package ops

import akka.stream.scaladsl._
import com.amazonaws.services.sqs.model.Message
import java.time.Instant
import scala.language.implicitConversions

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

trait TimestampedMessageOps {
  implicit def timestampedMessageSourceOps[Mat](source: Source[Message, Mat]): TimestampedMessageSource[Mat] =
    new TimestampedMessageSource[Mat](source)

  implicit def timestampedMessageFlowOps[In, Mat](flow: Flow[In, Message, Mat]): TimestampedMessageFlow[In, Mat] =
    new TimestampedMessageFlow[In, Mat](flow)
}

class TimestampedMessageSource[Mat](val source: Source[Message, Mat]) extends AnyVal {
  def timestampMessage(now: () => Instant = () => Instant.now): Source[RetrievedMessage, Mat] =
    source.map(message => RetrievedMessage(message, now()))
}

class TimestampedMessageFlow[In, Mat](val flow: Flow[In, Message, Mat]) extends AnyVal {
  def timestampMessage(now: () => Instant = () => Instant.now): Flow[In, RetrievedMessage, Mat] =
    flow.map(message => RetrievedMessage(message, now()))
}
