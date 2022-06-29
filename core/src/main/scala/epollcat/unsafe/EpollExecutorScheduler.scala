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

import cats.effect.unsafe.PollingExecutorScheduler

import java.util.Collections
import java.util.IdentityHashMap
import java.util.Set
import scala.annotation.nowarn
import scala.concurrent.duration._
import scala.scalanative.libc.errno
import scala.scalanative.posix.inttypes._
import scala.scalanative.runtime._
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.scalanative.posix.unistd

private[epollcat] final class EpollExecutorScheduler private (
    private[this] val epfd: Int,
    private[this] val maxEvents: Int)
    extends PollingExecutorScheduler {

  import epoll._

  private[this] val callbacks: Set[Runnable] = Collections.newSetFromMap(new IdentityHashMap)

  override def poll(timeout: Duration): Boolean = {
    val timeoutIsInfinite = timeout > Int.MaxValue.millis

    if (timeoutIsInfinite && callbacks.isEmpty()) false
    else {
      val timeoutMillis = if (timeoutIsInfinite) -1 else timeout.toMillis.toInt

      val events = stackalloc[Byte](maxEvents.toLong)

      val triggeredEvents = epoll_wait(epfd, events, maxEvents, timeoutMillis)

      if (triggeredEvents >= 0) {
        var i = 0
        while (i < triggeredEvents) {
          val event = events + i * 12
          val task = Intrinsics
            .castRawPtrToObject(toRawPtr(!((event + 4).asInstanceOf[Ptr[Ptr[Byte]]])))
            .asInstanceOf[Runnable]
          callbacks.remove(task)
          execute(task)
          i += 1
        }
      } else {
        reportFailure(new RuntimeException(s"epoll_wait: ${errno.errno}"))
      }

      !callbacks.isEmpty()
    }
  }

  def monitor(fd: Int, events: Int, task: Runnable): Runnable = {

    val event = stackalloc[Byte](12)
    !event.asInstanceOf[Ptr[UInt]] = events.toUInt
    !(event + 4).asInstanceOf[Ptr[Ptr[Unit]]] = fromRawPtr(Intrinsics.castObjectToRawPtr(task))

    epollCtl(epfd, EPOLL_CTL_ADD, fd, event)

    callbacks.add(task)

    () => {
      callbacks.remove(task)
      epollCtl(epfd, EPOLL_CTL_DEL, fd, null)
    }
  }

  @inline private[this] def epollCtl(epfd: Int, op: Int, fd: Int, event: Ptr[Byte]): Unit = {
    if (epoll_ctl(epfd, op, fd, event) != 0)
      throw new RuntimeException(s"epoll_ctl: ${errno.errno}")
  }

  def close(): Unit =
    if (unistd.close(epfd) != 0)
      throw new RuntimeException(s"close: ${errno.errno}")

}

private[epollcat] object EpollExecutorScheduler {

  import epoll._

  def apply(): EpollExecutorScheduler = apply(64)

  def apply(maxEvents: Int): EpollExecutorScheduler = {
    val epfd = epoll_create1(0)
    new EpollExecutorScheduler(epfd, maxEvents)
  }

}

@extern
@nowarn
private[epollcat] object epoll {

  final val EPOLL_CTL_ADD = 1
  final val EPOLL_CTL_DEL = 2

  final val EPOLLIN = 0x001
  final val EPOLLOUT = 0x004
  final val EPOLLONESHOT = 1 << 30

  type epoll_data_t = Ptr[Unit]

  def epoll_create1(flags: Int): Int = extern

  def epoll_ctl(epfd: Int, op: Int, fd: Int, event: Ptr[Byte]): Int = extern

  def epoll_wait(epfd: Int, events: Ptr[Byte], maxevents: Int, timeout: Int): Int =
    extern

}
