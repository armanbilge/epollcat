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

package example

import cats.effect.IO
import cats.effect.std.Console
import epollcat.EpollApp

import scala.concurrent.duration._

object TpolChiclet extends EpollApp.Simple {

  def run = IO.ref(0).flatMap { counter =>
    val printism =
      counter.getAndUpdate(i => (i + 1) % 5).map(tpolecatisms(_)).flatMap(Console[IO].println)
    val printismsLoop = IO.sleep(1.second) *> (printism *> IO.sleep(3.seconds)).foreverM

    def readLoop: IO[Unit] = Console[IO].readLine.flatMap { l =>
      if (l == "return") IO.println("* throws laptop into the sea *")
      else IO.println("i'm a simple caveman, your complicated language confuses me") >> readLoop
    }

    val race = IO.race(printismsLoop, readLoop)

    race.void.timeoutTo(30.seconds, IO.println("ffs"))
  }

  val tpolecatisms = List(
    "Dijkstra would not have liked this.",
    "Traverse is one of the most beautiful things in all of computering.",
    "✨  COMPUTERS!  ✨     jazz hands",
    "damnit this isn't markup is it",
    "look we did fine without computers for 4 billion years"
  )

}
