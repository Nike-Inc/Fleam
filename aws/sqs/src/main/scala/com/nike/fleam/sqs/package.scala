package com.nike.fleam

import java.time.Instant
import java.util.concurrent.{Future => JavaFuture}
import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.sqs.model.Message
import scala.concurrent.{Future, Promise}

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

package sqs {
  case class EntryError(code: String, reason: String)

  case class RetrievedMessage(message: Message, timestamp: Instant)

  case class MessageId(value: String) extends AnyVal

  case class MessageGroupId(value: String) extends AnyVal
}

package object sqs {

  type OpError = Either[EntryError, SqsEnqueueError]

  def wrapRequest[Request <: AmazonWebServiceRequest, Result](
    f: (Request, AsyncHandler[Request, Result]) => JavaFuture[Result]): Request => Future[Result] = {

    request => {
      val promise = Promise[Result]()

      f(request, new AsyncHandler[Request, Result] {
        override def onError(exception: Exception): Unit = { promise.failure(exception); () }
        override def onSuccess(request: Request, result: Result) =
        { promise.success(result); () }
      })
      promise.future
    }
  }
}
