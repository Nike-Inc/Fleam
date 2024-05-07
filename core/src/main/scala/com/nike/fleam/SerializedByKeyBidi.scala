package com.nike.fleam

import org.apache.pekko.stream.stage._
import org.apache.pekko.stream.{Attributes, BidiShape, Inlet, Outlet}
import org.apache.pekko.stream.scaladsl.BidiFlow
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

  /** Provides a buffer that locks and unlocks values by key */
  trait MutableKeyBuffer[Key, Value] {
    /** Return the next available value if possible, locking the key at the time provided until it's unlocked.*/
    def next(atTime: Instant): Option[Value]
    /** Add an value by key */
    def add(key: Key, value: Value): Unit
    /** Should return true when all values have left and all locks are removed */
    def isEmpty(): Boolean
    /** Should return true when it can no longer hold more values. This is back-pressure additional values. */
    def isFull(): Boolean
    /** Unlock all values that are expired before the passed in time */
    def unlockExpired(time: Instant): Unit
    /** Unlock a key */
    def unlock(key: Key): Unit
  }

  /** A default `scala.collection.mutable.Queue` implementation of [[SerializedByKeyBidi.MutableKeyBuffer]] */
  def queueBuffer[Key, Value](size: Int, lockDuration: FiniteDuration) = new MutableKeyBuffer[Key, Value] {
    case class KeyedValue(key: Key, value: Value)
    case class TimeLockedKey(key: Key, unlockTime: Instant)

    val order = collection.mutable.Queue.empty[KeyedValue]
    val locked = collection.mutable.Queue.empty[TimeLockedKey]

    def next(atTime: Instant): Option[Value] =
      order
        .dequeueFirst(element => !locked.exists(_.key == element.key))
        .map { case KeyedValue(key, value) =>
          locked.enqueue(TimeLockedKey(key, unlockTime = atTime.plusMillis(lockDuration.toMillis)))
          value
        }

    def add(key: Key, value: Value): Unit = order.enqueue(KeyedValue(key,value))

    def isEmpty(): Boolean = order.isEmpty && locked.isEmpty

    def isFull(): Boolean = order.length >= size

    def unlockExpired(time: Instant): Unit = locked.dequeueAll(_.unlockTime.toEpochMilli < time.toEpochMilli)

    def unlock(key: Key): Unit = locked.dequeueFirst(_.key == key)
  }

/** Bidi Stage that limits items to a single item at a time by key using [[SerializedByKeyBidi.queueBuffer]]
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
 */
  def apply[Key, In : Keyed[*, Key], Out : Keyed[*, Key]](
    bufferSize: Int,
    expiration: FiniteDuration,
    expirationInterval: FiniteDuration,
    now: () => Instant = () => Instant.now): BidiFlow[In, In, Out, Out, org.apache.pekko.NotUsed] =
      BidiFlow.fromGraph(new SerializedByKeyBidi(queueBuffer(bufferSize, expiration), expirationInterval, now))
}

/** Bidi Stage that limits items to a single item at a time by key
 *
 *  Limits items to a single item at a time by key. This helps to prevent any concurrent operations to elements by key.
 *
 *  @param buffer a [[SerializedByKeyBidi.MutableKeyBuffer]] providing buffering and locking of values
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
 *  val updateFlow: Flow[DatabaseUpdate, Updated, org.apache.pekko.NotUsed] = Flow[DatabaseUpdate].mapAsync(10) { ??? }
 *
 *  val serializedByKey = new SerializedByKeyBidi(
 *    buffer = SerializedByKeyBidi.queueBuffer(size = 10, lockDuration = 1.seconds)
 *    experiationInterval = 500.millis
 *  )
 *
 *  val nonConcurrentUpdatesByKey: Flow[DatabaseUpdate, Updated, org.apache.pekko.NotUsed] = serializedByKey.join(updateFlow)
 *  }}}
 */
class SerializedByKeyBidi[Key, In : Keyed[*, Key], Out : Keyed[*, Key]](
  buffer: SerializedByKeyBidi.MutableKeyBuffer[Key, In],
  expirationInterval: FiniteDuration,
  now: () => Instant = () => Instant.now) extends GraphStage[BidiShape[In, In, Out, Out]] {

  val fromUpstream: Inlet[In] = Inlet("From Upstream")
  val toProcessing: Outlet[In] = Outlet("To Processing")
  val fromProcessing: Inlet[Out] = Inlet("From Processing")
  val toDownstream: Outlet[Out] = Outlet("To Downstream")

  override val shape = BidiShape(fromUpstream, toProcessing, fromProcessing, toDownstream)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new TimerGraphStageLogic(shape) {
    var complete = false

    override def preStart(): Unit = {
      setKeepGoing(true)
      scheduleAtFixedRate(None, expirationInterval, expirationInterval)
    }

    override protected def onTimer(timerKey: Any): Unit = {
      buffer.unlockExpired(now())
      safePushToProcessing()
    }

    def safePushToProcessing(): Unit =
      if (isAvailable(toProcessing)) {
        buffer.next(now()).map(push(toProcessing, _))
      }

    def safePullFromUpstream() = {
      if (!hasBeenPulled(fromUpstream) && !complete && !buffer.isFull()) { pull(fromUpstream) }
    }

    setHandler(fromUpstream, new InHandler {
      override def onPush(): Unit = {
        val in = grab(fromUpstream)
        buffer.add(implicitly[Keyed[In, Key]].getKey(in), in)
        safePushToProcessing()
        safePullFromUpstream()
      }

      override def onUpstreamFinish(): Unit = {
        complete = true
        // This can come after the stage has no more elements to process if the queue is empty
        // making this our only chance to complete
        if (buffer.isEmpty()) {
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
        buffer.unlock(key)
        push(toDownstream, element)
        safePushToProcessing()
        if (complete && buffer.isEmpty()) {
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
