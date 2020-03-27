package com.nike.fleam.sqs

import java.time.Instant
import simulacrum._
import scala.language.implicitConversions
import scala.concurrent.duration._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

@typeclass trait RetrievedTime[-T] { self =>
  def getRetrievedTime(t: T): Instant
  def getElaspsedTime(t: T)(now: Instant): Duration =
    (now.toEpochMilli - self.getRetrievedTime(t).toEpochMilli).millis
}

object RetrievedTime {
  def lift[T](f: T => Instant) = new RetrievedTime[T] {
    def getRetrievedTime(t: T): Instant = f(t)
  }

  implicit def eitherRetrievedTime[L: RetrievedTime, R: RetrievedTime] = lift[Either[L, R]] {
    _.fold(RetrievedTime[L].getRetrievedTime, RetrievedTime[R].getRetrievedTime)
  }
}
