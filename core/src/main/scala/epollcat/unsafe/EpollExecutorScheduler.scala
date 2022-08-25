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
import scala.concurrent.duration._
import scala.scalanative.libc.errno
import scala.scalanative.posix.unistd
import scala.scalanative.runtime._
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

import epoll._
import epollImplicits._

private[epollcat] final class EpollExecutorScheduler private (
    private[this] val epfd: Int,
    private[this] val maxEvents: Int)
    extends PollingExecutorScheduler {

  private[this] val callbacks: Set[Int => Unit] = Collections.newSetFromMap(new IdentityHashMap)

  def poll(timeout: Duration): Boolean = {

    val timeoutMillis = if (timeout == Duration.Inf) -1 else timeout.toMillis.toInt

    val events = stackalloc[epoll_event](maxEvents.toUInt)

    val triggeredEvents = epoll_wait(epfd, events, maxEvents, timeoutMillis)

    if (triggeredEvents >= 0) {
      var i = 0
      while (i < triggeredEvents) {
        val event = events + i.toLong
        val cb = fromPtr[Int => Unit](event.data)
        cb(event.events.toInt)
        i += 1
      }
    } else {
      reportFailure(new RuntimeException(s"epoll_wait: ${errno.errno}"))
    }

    !callbacks.isEmpty()
  }

  def ctl(fd: Int, events: Int)(cb: Int => Unit): Runnable = {
    val event = stackalloc[epoll_event]()
    event.events = events.toUInt
    event.data = toPtr(cb)

    if (epoll_ctl(epfd, EPOLL_CTL_ADD, fd, event) != 0)
      throw new RuntimeException(s"epoll_ctl: ${errno.errno}")
    callbacks.add(cb)

    () => {
      if (epoll_ctl(epfd, EPOLL_CTL_DEL, fd, null) != 0)
        throw new RuntimeException(s"epoll_ctl: ${errno.errno}")
      callbacks.remove(cb)
      ()
    }
  }

  def close(): Unit =
    if (unistd.close(epfd) != 0)
      throw new RuntimeException(s"close: ${errno.errno}")

  private def toPtr(a: AnyRef): Ptr[Byte] =
    fromRawPtr(Intrinsics.castObjectToRawPtr(a))

  private def fromPtr[A](ptr: Ptr[Byte]): A =
    Intrinsics.castRawPtrToObject(toRawPtr(ptr)).asInstanceOf[A]
}

private[epollcat] object EpollExecutorScheduler {

  import epoll._

  final val Read = epoll.EPOLLIN
  final val Write = epoll.EPOLLOUT
  final val EdgeTriggered = epoll.EPOLLET

  def apply(): EpollExecutorScheduler = apply(64)

  def apply(maxEvents: Int): EpollExecutorScheduler = {
    val epfd = epoll_create1(0)
    if (epfd == -1)
      throw new RuntimeException(s"epoll_create1: ${errno.errno}")
    new EpollExecutorScheduler(epfd, maxEvents)
  }

}
