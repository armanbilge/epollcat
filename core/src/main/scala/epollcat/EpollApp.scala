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

import cats.effect.IOApp
import cats.effect.unsafe.IORuntime
import epollcat.unsafe.EpollRuntime

@deprecated("Upgrade to Cats Effect v3.6.0 and use IOApp", "0.1.7")
trait EpollApp extends IOApp {

  override final lazy val runtime: IORuntime = {
    val installed = EpollRuntime installGlobal {
      EpollRuntime(runtimeConfig)
    }

    if (!installed) {
      System
        .err
        .println(
          "WARNING: Epollcat global runtime already initialized; custom configurations will be ignored")
    }

    EpollRuntime.global
  }

}

@deprecated("Upgrade to Cats Effect v3.6.0 and use IOApp", "0.1.7")
object EpollApp {
  trait Simple extends IOApp.Simple with EpollApp
}
