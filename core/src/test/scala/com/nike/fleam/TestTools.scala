package com.nike.fleam

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.{ActorAttributes, Materializer}
import org.apache.pekko.stream.Supervision.Resume
import com.typesafe.config.ConfigFactory

import scala.concurrent.{Future, ExecutionContext, Promise}

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

object TestTools {

  val config = ConfigFactory.load()
  val ResumeSupervisionStrategy = ActorAttributes.supervisionStrategy( _ => Resume)

  implicit val actorSystem: ActorSystem = ActorSystem("test", config)
  implicit val executionContext: ExecutionContext = actorSystem.dispatcher

  /**
   * When Fleam used Akka 2.5+, the materializer below used to have a supervision policy that was equivalent to
   * [[ResumeSupervisionStrategy]] above. As materializers come about differently in Akka 2.6+,
   * any such policy would be set on the stream itself (see [[SerializedByKeyBidiTest]] for example).
   * Also see the Akka Migration Guide 2.5.x to 2.6.x
   * [[https://doc.akka.io/docs/akka/current/project/migration-guide-2.5.x-2.6.x.html]]
   */
  implicit val materializer : Materializer = Materializer.matFromSystem(actorSystem)

  def checkSideEffect[A, B](process: Future[A], promise: Promise[B]): Future[B] =
    for {
      _ <- process
      result <- promise.future
    } yield  {
      result
    }
}
