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

import cats.effect.unsafe.IORuntime
import cats.effect.unsafe.IORuntimeConfig
import cats.effect.unsafe.Scheduler

import scala.concurrent.ExecutionContext

object EpollIORuntime {
  def defaultExecutionContextScheduler(): ExecutionContext with Scheduler =
    EpollExecutorScheduler()

  private[this] var _global: IORuntime = null

  private[epollcat] def installGlobal(global: => IORuntime): Boolean = {
    if (_global == null) {
      _global = global
      true
    } else {
      false
    }
  }

  private[epollcat] def resetGlobal(): Unit =
    _global = null

  lazy val global: IORuntime = {
    if (_global == null) {
      val ecScheduler = defaultExecutionContextScheduler()

      installGlobal {
        IORuntime(ecScheduler, ecScheduler, ecScheduler, () => (), IORuntimeConfig())
      }
    }

    _global
  }
}
