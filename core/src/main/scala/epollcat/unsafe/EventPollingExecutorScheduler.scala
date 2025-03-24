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

package epollcat
package unsafe

import cats.effect.unsafe.PollingExecutorScheduler

import scala.annotation.nowarn
import scala.scalanative.annotation.alwaysinline
import scala.scalanative.meta.LinktimeInfo
import scala.scalanative.runtime._
import scala.scalanative.unsafe._

@nowarn
private[epollcat] abstract class EventPollingExecutorScheduler(pollEvery: Int)
    extends PollingExecutorScheduler(pollEvery) {

  def monitor(fd: Int, reads: Boolean, writes: Boolean)(cb: EventNotificationCallback): Runnable

}

private[epollcat] trait EventNotificationCallback {
  protected[epollcat] def notifyEvents(readReady: Boolean, writeReady: Boolean): Unit
}

private[epollcat] object EventNotificationCallback {
  @alwaysinline private[unsafe] def toPtr(cb: EventNotificationCallback): Ptr[Byte] =
    fromRawPtr(Intrinsics.castObjectToRawPtr(cb))

  @alwaysinline private[unsafe] def fromPtr[A](ptr: Ptr[Byte]): EventNotificationCallback =
    Intrinsics.castRawPtrToObject(toRawPtr(ptr)).asInstanceOf[EventNotificationCallback]
}

private[epollcat] object EventPollingExecutorScheduler {

  def apply(pollEvery: Int, maxEvents: Int): (EventPollingExecutorScheduler, () => Unit) =
    if (LinktimeInfo.isLinux)
      EpollExecutorScheduler(pollEvery, maxEvents)
    else if (LinktimeInfo.isMac)
      KqueueExecutorScheduler(pollEvery, maxEvents)
    else
      throw new UnsupportedPlatformError

}
