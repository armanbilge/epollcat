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

import cats.effect.kernel.Async
import cats.syntax.all._
import epollcat.unsafe.EpollExecutorScheduler

import Epoll._

trait Epoll[F[_]] {

  def monitor(fd: Int, event: Event): F[Unit]

}

object Epoll {
  import unsafe.epoll._

  sealed abstract class Event private (private[epollcat] val mask: Int)
  object Event {

    case object EpollIn extends Event(EPOLLIN)
    case object EpollOut extends Event(EPOLLOUT)
  }

  private[epollcat] def apply[F[_]](epoll: EpollExecutorScheduler)(
      implicit F: Async[F]): Epoll[F] =
    new Epoll[F] {
      def monitor(fd: Int, event: Event): F[Unit] = F.async[Unit] { cb =>
        F.delay(epoll.monitor(fd, event.mask | EPOLLONESHOT, () => cb(Right(())))).map {
          cancel => Some(F.delay(cancel.run()))
        }
      }
    }

}
