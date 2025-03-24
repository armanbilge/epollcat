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

@deprecated("Upgrade to Cats Effect v3.6.0 and use IORuntime", "0.1.7")
object EpollRuntime {

  def apply(): IORuntime = apply(IORuntimeConfig())

  def apply(config: IORuntimeConfig): IORuntime = {
    val (ecScheduler, shutdown) = defaultExecutionContextScheduler()
    IORuntime(ecScheduler, ecScheduler, ecScheduler, shutdown, config)
  }

  def defaultExecutionContextScheduler(): (ExecutionContext with Scheduler, () => Unit) = {
    EventPollingExecutorScheduler(64, 64)
  }

  private[this] var _global: IORuntime = null

  private[epollcat] def installGlobal(global: => IORuntime): Boolean = {
    if (_global == null) {
      _global = global
      true
    } else {
      false
    }
  }

  lazy val global: IORuntime = {
    if (_global == null) {
      installGlobal {
        EpollRuntime()
      }
      ()
    }

    _global
  }

}
