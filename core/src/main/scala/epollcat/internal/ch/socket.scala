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

package epollcat.internal.ch

import scala.annotation.nowarn
import scala.scalanative.unsafe._
import scala.scalanative.posix.sys.socket._

@extern
@nowarn
private[ch] object socket {
  final val SOCK_NONBLOCK = 2048 // only in Linux and FreeBSD, but not macOS

  // only supported on Linux and FreeBSD, but not macOS
  def accept4(sockfd: CInt, addr: Ptr[sockaddr], addrlen: Ptr[socklen_t], flags: CInt): CInt =
    extern
}
