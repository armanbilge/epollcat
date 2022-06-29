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
import cats.effect.unsafe.IORuntime
import epollcat.unsafe.EpollIORuntime
import munit.CatsEffectSuite

class EpollcatSuite extends CatsEffectSuite {

  override implicit def munitIoRuntime: IORuntime = EpollIORuntime.global

  test("ceding") {
    val result = IO.ref[List[String]](Nil).flatMap { ref =>
      def go(s: String) = (ref.getAndUpdate(s :: _) *> IO.cede).replicateA_(3)
      IO.both(go("ping"), go("pong")) *> ref.get
    }

    result.assertEquals(List("pong", "ping", "pong", "ping", "pong", "ping"))
  }

}
