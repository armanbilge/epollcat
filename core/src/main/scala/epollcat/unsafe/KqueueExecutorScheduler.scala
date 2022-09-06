/*
 * Copyright 2022 Arman Bilge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package epollcat.unsafe

import java.util.ArrayDeque
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Set
import scala.concurrent.duration._
import scala.scalanative.libc.errno
import scala.scalanative.posix.time
import scala.scalanative.posix.timeOps._
import scala.scalanative.posix.unistd
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.util.control.NonFatal

import KqueueExecutorScheduler._
import kqueue._
import kqueueImplicits._

private[unsafe] final class KqueueExecutorScheduler(
    private[this] val kqfd: Int,
    private[this] val maxEvents: Int)
    extends EventPollingExecutorScheduler {

  private[this] val changes: ArrayDeque[Change] = new ArrayDeque
  private[this] val callbacks: Set[EventNotificationCallback] =
    Collections.newSetFromMap(new IdentityHashMap)

  def poll(timeout: Duration): Boolean = {
    val timeoutIsInfinite = timeout == Duration.Inf
    val timeoutIsZero = timeout == Duration.Zero
    val noCallbacks = callbacks.isEmpty()
    val changeCount = changes.size()

    if ((timeoutIsInfinite || timeoutIsZero) && noCallbacks && changeCount == 0)
      false // nothing to do here
    else {

      val changelist = stackalloc[kevent64_s](changeCount.toLong)
      var change = changelist
      while (!changes.isEmpty()) {
        changes.poll() match {
          case Monitor(fd, filter, cb) =>
            change.ident = fd.toULong
            change.filter = filter
            change.flags = EV_ADD.toUShort
            change.udata = EventNotificationCallback.toPtr(cb)
            callbacks.add(cb)
          case Unmonitor(fd, cb) =>
            change.ident = fd.toULong
            change.flags = EV_DELETE.toUShort
            callbacks.remove(cb)
        }
        change += 1
      }

      val timeoutSpec =
        if (timeoutIsInfinite || timeoutIsZero) null
        else {
          val ts = stackalloc[time.timespec]()
          val sec = timeout.toSeconds
          ts.tv_sec = sec
          ts.tv_nsec = (timeout - sec.seconds).toNanos
          ts
        }

      val eventlist = stackalloc[kevent64_s](maxEvents.toLong)
      val flags = (if (timeoutIsZero) KEVENT_FLAG_IMMEDIATE else KEVENT_FLAG_NONE).toUInt
      val triggeredEvents =
        kevent64(kqfd, changelist, changeCount, eventlist, maxEvents, flags, timeoutSpec)

      if (triggeredEvents >= 0) {
        var i = 0
        var event = eventlist
        while (i < triggeredEvents) {
          val cb = EventNotificationCallback.fromPtr(event.udata)
          try {
            val filter = event.filter
            cb.notifyEvents(filter == EVFILT_READ, filter == EVFILT_WRITE)
          } catch {
            case NonFatal(ex) => reportFailure(ex)
          }
          i += 1
          event += 1
        }
      } else {
        throw new RuntimeException(s"kevent64: ${errno.errno}")
      }

      !callbacks.isEmpty()
    }
  }

  def monitor(fd: Int, reads: Boolean, writes: Boolean)(
      cb: EventNotificationCallback): Runnable = {
    if (reads)
      changes.add(Monitor(fd, EVFILT_READ, cb))
    if (writes)
      changes.add(Monitor(fd, EVFILT_WRITE, cb))
    () => { changes.add(Unmonitor(fd, cb)); () }
  }

}

private[unsafe] object KqueueExecutorScheduler {

  def apply(maxEvents: Int): (KqueueExecutorScheduler, () => Unit) = {
    val kqfd = kqueue()
    if (kqfd == -1)
      throw new RuntimeException(s"kqfd: ${errno.errno}")
    val kqec = new KqueueExecutorScheduler(kqfd, maxEvents)
    val shutdown = () => {
      if (unistd.close(kqfd) != 0) throw new RuntimeException(s"close: ${errno.errno}")
    }
    (kqec, shutdown)
  }

  private sealed abstract class Change
  private final case class Monitor(
      fd: Int,
      filter: Short,
      cb: EventNotificationCallback
  ) extends Change
  private final case class Unmonitor(fd: Int, cb: EventNotificationCallback) extends Change

}
