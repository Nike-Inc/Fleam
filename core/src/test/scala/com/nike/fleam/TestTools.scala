package com.nike.fleam

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import com.typesafe.config.ConfigFactory
import scala.concurrent.{Future, Promise}

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

object TestTools {

  val config = ConfigFactory.load()
  implicit val actorSystem = ActorSystem("test", config)
  implicit val executionContext = actorSystem.dispatcher
  implicit val materializer = ActorMaterializer(
    ActorMaterializerSettings(actorSystem).withSupervisionStrategy(_ => Supervision.Resume)
  )
  def checkSideEffect[A, B](process: Future[A], promise: Promise[B]): Future[B] =
    for {
      _ <- process
      result <- promise.future
    } yield  {
      result
    }
}
