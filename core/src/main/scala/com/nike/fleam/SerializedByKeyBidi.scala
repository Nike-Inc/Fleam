package com.nike.fleam

import akka.stream.stage._
import akka.stream.{Attributes, BidiShape, Inlet, Outlet}
import akka.stream.scaladsl.BidiFlow
import scala.concurrent.duration._
import java.time.Instant

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

/** Factory for BidiFlow of [[com.nike.fleam.SerializedByKeyBidi]] instances */
object SerializedByKeyBidi {
  def apply[Key, In : Keyed[?, Key], Out : Keyed[?, Key]](
    bufferSize: Int,
    expiration: FiniteDuration,
    expirationInterval: FiniteDuration,
    now: () => Instant = () => Instant.now): BidiFlow[In, In, Out, Out, akka.NotUsed] =
      BidiFlow.fromGraph(new SerializedByKeyBidi(bufferSize, expiration, expirationInterval, now))
}

/** Bidi Stage that limits items to a single item at a time by key
 *
 *  Limits items to a single item at a time by key. This helps to prevent any concurrent operations to elements by key.
 *
 *  @param bufferSize Number of elements to hold in buffer while waiting for duplicate keys to exit.
 *                    Should probably be at least as large as your parallel async operations downstream.
 *  @param expiration Amount of time a element moving through the connected flow will be considered still processing.
 *                    Should probably be longer than the sum any future operations are expected to run.
 *  @param expirationInterval Interval between expiration checks. Should probably be smaller than your expiration time.
 *                            Actual expiration time could be as large as expiration + expirationInterval.
 *  @param now A function to get the current time. Defaults to `Instant.now`.
 *
 *  {{{
 *  // Make database updates without worrying about concurrent updates for the same key
 *
 *  case class DatabaseUpdate(id: String, score: Int)
 *  case class Updated(id: String)
 *
 *  implicit val databaseKeyed: Keyed[DatabaseUpdate, String] = Keyed.lift[DatabaseUpdate, String](_.id)
 *  implicit val updatedKeyed: Keyed[Updated, String] = Keyed.lift[Updated, String](_.id)
 *
 *  val updateFlow: Flow[DatabaseUpdate, Updated, akka.NotUsed] = Flow[DatabaseUpdate].mapAsync(10) { ??? }
 *
 *  val serializedByKey = SerializedByKeyBidi(
 *    bufferSize = 10,
 *    expiration = 1.seconds,
 *    experiationInterval = 500.millis
 *  )
 *
 *  val nonConcurrentUpdatesByKey: Flow[DatabaseUpdate, Updated, akka.NotUsed] = serializedByKey.join(updateFlow)
 *  }}}
 */
class SerializedByKeyBidi[Key, In : Keyed[?, Key], Out : Keyed[?, Key]](
  bufferSize: Int,
  expiration: FiniteDuration,
  expirationInterval: FiniteDuration,
  now: () => Instant = () => Instant.now) extends GraphStage[BidiShape[In, In, Out, Out]] {

  case class KeyedIn(key: Key, in: In)
  case class TimeLockedKey(key: Key, lockTime: Instant)

  val fromUpstream: Inlet[In] = Inlet("From Upstream")
  val toProcessing: Outlet[In] = Outlet("To Processing")
  val fromProcessing: Inlet[Out] = Inlet("From Processing")
  val toDownstream: Outlet[Out] = Outlet("To Downstream")

  override val shape = BidiShape(fromUpstream, toProcessing, fromProcessing, toDownstream)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new TimerGraphStageLogic(shape) {
    val order = collection.mutable.Queue.empty[KeyedIn]
    val locked = collection.mutable.Queue.empty[TimeLockedKey]
    var complete = false

    override def preStart: Unit = {
      setKeepGoing(true)
      schedulePeriodically(None, expirationInterval)
    }

    override protected def onTimer(timerKey: Any): Unit = {
      unlockExpired()
      safePushToProcessing()
    }


    def buffer() = {
      val element = grab(fromUpstream)
      order.enqueue(KeyedIn(key = implicitly[Keyed[In, Key]].getKey(element), in = element))
    }

    def safePushToProcessing(): Unit = {
      if (isAvailable(toProcessing)) {
        order
          .dequeueFirst(element => !locked.exists(_.key == element.key))
          .map { element =>
            locked.enqueue(TimeLockedKey(element.key, lockTime = now()))
            push(toProcessing, element.in)
          }
      }
    }

    def safePullFromUpstream() = {
      if (!hasBeenPulled(fromUpstream) && !complete && order.length < bufferSize) { pull(fromUpstream) }
    }

    def unlockExpired() = {
      val expirationTime = now().toEpochMilli - expiration.toMillis
      locked.dequeueAll(_.lockTime.toEpochMilli < expirationTime)
    }

    setHandler(fromUpstream, new InHandler {
      override def onPush(): Unit = {
        buffer()
        safePushToProcessing()
        safePullFromUpstream()
      }

      override def onUpstreamFinish(): Unit = {
        complete = true
        // This can come after the stage has no more elements to process if the queue is empty
        // making this our only chance to complete
        if (order.isEmpty && locked.isEmpty) {
          completeStage()
        }
      }
    })

    setHandler(toProcessing, new OutHandler {
      override def onPull(): Unit = {
        safePushToProcessing()
        safePullFromUpstream()
      }
    })

    setHandler(fromProcessing, new InHandler {
      override def onPush(): Unit = {
        val element = grab(fromProcessing)
        val key = implicitly[Keyed[Out, Key]].getKey(element)
        locked.dequeueFirst(_.key == key)
        push(toDownstream, element)
        safePushToProcessing()
        if (complete && order.isEmpty && locked.isEmpty) {
          completeStage()
        }
      }
    })

    setHandler(toDownstream, new OutHandler {
      override def onPull(): Unit = {
        pull(fromProcessing)
      }
    })
  }
}
