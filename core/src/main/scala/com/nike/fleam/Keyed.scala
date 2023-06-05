package com.nike.fleam

import scala.language.implicitConversions

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

trait Keyed[-T, +Key] {
  def getKey(t: T): Key
}

object Keyed {

  trait Ops[T, Key] {
    def typeClassInstance: Keyed[T, Key]
    def self: T
    def getKey: Key = typeClassInstance.getKey(self)
  }

  trait ToKeyedOps {
    implicit def toKeyedOps[T, Key](target: T)(implicit tc: Keyed[T, Key]): Ops[T, Key] = new Ops[T, Key] {
      val self = target
      val typeClassInstance = tc
    }
  }

  object ops {
    implicit def toKeyedOps[T, Key](target: T)(implicit tc: Keyed[T, Key]): Ops[T, Key] = new Ops[T, Key] {
      val self = target
      val typeClassInstance = tc
    }
  }

  def lift[T, Key](f: T => Key) = new Keyed[T, Key] {
    def getKey(t: T): Key = f(t)
  }

  implicit def keyedEither[Key, L: Keyed[*, Key], R: Keyed[*, Key]]: Keyed[Either[L, R], Key] =
    Keyed.lift(_.fold(implicitly[Keyed[L, Key]].getKey, implicitly[Keyed[R, Key]].getKey))
}
