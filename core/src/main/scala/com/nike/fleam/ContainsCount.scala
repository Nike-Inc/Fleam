package com.nike.fleam

import scala.language.implicitConversions
import cats.implicits._

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

trait ContainsCount[T, F[_]] {
  def getCount(t: T): F[Int]
  def setCount(t: T)(count: Int): T
}

object ContainsCount {

  trait Ops[T, F[_]] {
    def typeClassInstance: ContainsCount[T, F]
    def self: T
    def getCount: F[Int] = typeClassInstance.getCount(self)
    def setCount(count: Int): T = typeClassInstance.setCount(self)(count)
  }

  trait ToContainsCountOps {
    implicit def toContainsCountOps[T, F[_]](target: T)(implicit tc: ContainsCount[T, F]): Ops[T, F] = new Ops[T, F] {
      val self = target
      val typeClassInstance = tc
    }
  }

  object ops {
    implicit def toContainsCountOps[T, F[_]](target: T)(implicit tc: ContainsCount[T, F]): Ops[T, F] = new Ops[T, F] {
      val self = target
      val typeClassInstance = tc
    }
  }

  implicit def eitherContainsCount[F[_], L: ContainsCount[?, F], R: ContainsCount[?, F]] =
    new ContainsCount[Either[L, R], F] {
      def getCount(e: Either[L, R]) = e.fold(implicitly[ContainsCount[L, F]].getCount, implicitly[ContainsCount[R, F]].getCount)
      def setCount(e: Either[L, R])(count: Int) =
        e.bimap(implicitly[ContainsCount[L, F]].setCount(_)(count), implicitly[ContainsCount[R, F]].setCount(_)(count))
    }
}
