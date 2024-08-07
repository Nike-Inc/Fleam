package com.nike.fleam

import org.apache.pekko.stream.{Materializer, Supervision}
import scala.concurrent.Future

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

trait SimplifiedStreamDeamon[SinkOut] {
  def registerShutdownHook(): Unit =
    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable() {
      override def run(): Unit = stop()
    }))
  def start(implicit materializer: Materializer): Future[SinkOut]
  def start(supervisionStrategy: Supervision.Decider)(implicit materializer: Materializer): Future[SinkOut]
  def stop(): Future[Unit]
  registerShutdownHook()
}
