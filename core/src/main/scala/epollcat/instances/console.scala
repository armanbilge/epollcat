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
package instances

import cats.Show
import cats.effect.std.Console
import cats.syntax.all._

import java.nio.charset.Charset
import cats.effect.kernel.Resource
import cats.effect.kernel.MonadCancelThrow

object console extends ConsoleInstances

trait ConsoleInstances {

  implicit def epollConsole[F[_]](
      implicit F: MonadCancelThrow[F],
      console: Console[F],
      epoll: Epoll[F]): Resource[F, Console[F]] =
    epoll.register(0).map { ctl =>
      new Console[F] {

        def readLineWithCharset(charset: Charset): F[String] =
          F.uncancelable { poll =>
            poll(ctl.monitor(Epoll.Event.EpollIn)) *> console.readLineWithCharset(charset)
          }

        def print[A](a: A)(implicit S: Show[A]): F[Unit] = console.print(a)

        def println[A](a: A)(implicit S: Show[A]): F[Unit] = console.println(a)

        def error[A](a: A)(implicit S: Show[A]): F[Unit] = console.error(a)

        def errorln[A](a: A)(implicit S: Show[A]): F[Unit] = console.errorln(a)

      }
    }

}
