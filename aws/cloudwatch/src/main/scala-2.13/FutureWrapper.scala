package com.nike.fleam.cloudwatch

import scala.concurrent.Future
import java.util.concurrent.{CompletionStage => JavaFuture}
import scala.jdk.FutureConverters._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

trait FutureWrapper {
  // $COVERAGE-OFF$ Not worth testing
  private[cloudwatch] def wrapRequest[Request, Response](f: Request => JavaFuture[Response]): Request => Future[Response] =
    request => f(request).asScala
  // $COVERAGE-ON$
}
