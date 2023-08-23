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

import org.typelevel.scalaccompat.annotation._

import scala.scalanative.unsafe._
import scala.scalanative.posix.sys.socket._

@extern
@nowarn212("cat=unused")
private[ch] object socket {
  final val SOCK_NONBLOCK = 2048 // only in Linux and FreeBSD, but not macOS

  // only on Linux and FreeBSD, but not macOS
  final val MSG_NOSIGNAL = 0x4000 /* Do not generate SIGPIPE */

  // only on macOS and some BSDs (?)
  final val SO_NOSIGPIPE = 0x1022 /* APPLE: No SIGPIPE on EPIPE */

  // only supported on Linux and FreeBSD, but not macOS
  @name("epollcat_accept4") // can remove glue code in SN 0.5
  def accept4(sockfd: CInt, addr: Ptr[sockaddr], addrlen: Ptr[socklen_t], flags: CInt): CInt =
    extern
}
