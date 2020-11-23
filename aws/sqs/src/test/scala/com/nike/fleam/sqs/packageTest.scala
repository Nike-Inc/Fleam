package com.nike.fleam.sqs

import java.util.concurrent.{Callable, Executors, Future => JavaFuture}

import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.OptionValues

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class packageTest extends AnyFlatSpec with Matchers with ScalaFutures with OptionValues {
  behavior of "wrapRequest"
  import scala.concurrent.ExecutionContext.Implicits.global

  val executor = Executors.newSingleThreadExecutor()

  def mockRequest[Request <: AmazonWebServiceRequest, Result](results: List[Result]): (Request, AsyncHandler[Request, Result]) => JavaFuture[Result] = {

    val remainingResults = scala.collection.mutable.ListBuffer[Result](results: _*)

    (request: Request, handler: AsyncHandler[Request, Result]) => {
      executor.submit(new Callable[Result] {
        override def call(): Result = synchronized {
          val result = remainingResults.head
          remainingResults.remove(0)
          handler.onSuccess(request, result)
          result
        }
      })
    }

  }

  it should "use a new promise for each invocation" in {
      val m =  wrapRequest[ReceiveMessageRequest, String](mockRequest(List("a", "b")))
      val results = for {
        first <- m(null)
        second <- m(null)
      } yield (first, second)

        whenReady (results) {
          case (first, second) =>
            first shouldEqual "a"
            second shouldEqual "b"
        }
  }
}
