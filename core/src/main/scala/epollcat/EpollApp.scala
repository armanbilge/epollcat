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

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.std.Console
import cats.effect.unsafe.IORuntime
import epollcat.unsafe.EpollExecutorScheduler
import epollcat.unsafe.EpollRuntime

trait EpollApp extends IOApp {

  override final lazy val runtime: IORuntime = EpollRuntime(runtimeConfig)

  implicit final lazy val epoll: Epoll[IO] =
    Epoll(runtime.compute.asInstanceOf[EpollExecutorScheduler])

  implicit final lazy val console: Console[IO] =
    instances
      .console
      .epollConsole(IO.asyncForIO, IO.consoleForIO, epoll)
      .allocated
      .map(_._1)
      .syncStep(runtimeConfig.autoYieldThreshold)
      .unsafeRunSync() // craziness
      .toOption
      .get

}

object EpollApp {
  trait Simple extends IOApp.Simple with EpollApp
}
