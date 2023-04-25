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

import java.util.Collections
import java.util.IdentityHashMap
import java.util.Set
import scala.concurrent.duration._
import scala.scalanative.libc.errno
import scala.scalanative.posix.unistd
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.util.control.NonFatal

import epoll._
import epollImplicits._

private[unsafe] final class EpollExecutorScheduler private (
    private[this] val epfd: Int,
    pollEvery: Int,
    private[this] val maxEvents: Int)
    extends EventPollingExecutorScheduler(pollEvery) {

  private[this] val callbacks: Set[EventNotificationCallback] =
    Collections.newSetFromMap(new IdentityHashMap)

  def poll(timeout: Duration): Boolean = {
    println("starting poll")
    val timeoutIsInfinite = timeout == Duration.Inf
    val noCallbacks = callbacks.isEmpty()

    if ((timeoutIsInfinite || timeout == Duration.Zero) && noCallbacks)
      false // nothing to do here. refer to scaladoc on PollingExecutorScheduler#poll
    else {
      val timeoutMillis = if (timeoutIsInfinite) -1 else timeout.toMillis.toInt

      val events = stackalloc[epoll_event](maxEvents.toUInt)

      println("about to wait")
      val triggeredEvents = epoll_wait(epfd, events, maxEvents, timeoutMillis)
      println("waited")

      if (triggeredEvents >= 0) {
        var i = 0
        println(s"triggered events $triggeredEvents")
        while (i < triggeredEvents) {
          val event = events + i.toLong
          println("retrieving callback")
          println(event.data)
          val cb = EventNotificationCallback.fromPtr(event.data)
          println("retrievied callback")
          try {
            val e = event.events.toInt
            val readReady = (e & EPOLLIN) != 0
            val writeReady = (e & EPOLLOUT) != 0
            println("calling callback")
            cb.notifyEvents(readReady, writeReady)
            println("called callback")
          } catch {
            case NonFatal(ex) => reportFailure(ex)
          }
          i += 1
        }
      } else {
        throw new RuntimeException(s"epoll_wait: ${errno.errno}")
      }

      !callbacks.isEmpty()
    }
  }

  def monitor(fd: Int, reads: Boolean, writes: Boolean)(
      cb: EventNotificationCallback): Runnable = {
    val event = stackalloc[epoll_event]()
    event.events =
      (EPOLLET | (if (reads) EPOLLIN else 0) | (if (writes) EPOLLOUT else 0)).toUInt
    event.data = EventNotificationCallback.toPtr(cb)

    println("adding fd to epoll")
    if (epoll_ctl(epfd, EPOLL_CTL_ADD, fd, event) != 0)
      throw new RuntimeException(s"epoll_ctl: ${errno.errno}")
    callbacks.add(cb)
    println("added fd to epoll")

    () => {
      if (epoll_ctl(epfd, EPOLL_CTL_DEL, fd, null) != 0)
        throw new RuntimeException(s"epoll_ctl: ${errno.errno}")
      callbacks.remove(cb)
      ()
    }
  }

}

private[unsafe] object EpollExecutorScheduler {

  def apply(pollEvery: Int, maxEvents: Int): (EpollExecutorScheduler, () => Unit) = {
    val epfd = epoll_create1(0)
    if (epfd == -1)
      throw new RuntimeException(s"epoll_create1: ${errno.errno}")
    val epoll = new EpollExecutorScheduler(epfd, pollEvery, maxEvents)
    val shutdown = () => {
      if (unistd.close(epfd) != 0) throw new RuntimeException(s"close: ${errno.errno}")
    }
    (epoll, shutdown)
  }

}
