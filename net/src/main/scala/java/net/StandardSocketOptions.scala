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

package java.net

object StandardSocketOptions {

  val SO_RCVBUF: SocketOption[java.lang.Integer] =
    new StdSocketOption("SO_RCVBUF", classOf)

  val SO_SNDBUF: SocketOption[java.lang.Integer] =
    new StdSocketOption("SO_SNDBUF", classOf)

  val SO_REUSEADDR: SocketOption[java.lang.Boolean] =
    new StdSocketOption("SO_REUSEADDR", classOf)

  val SO_REUSEPORT: SocketOption[java.lang.Boolean] =
    new StdSocketOption("SO_REUSEPORT", classOf)

  val SO_KEEPALIVE: SocketOption[java.lang.Boolean] =
    new StdSocketOption("SO_KEEPALIVE", classOf)

  val TCP_NODELAY: SocketOption[java.lang.Boolean] =
    new StdSocketOption("TCP_NODELAY", classOf)

  private final class StdSocketOption[T](val name: String, val `type`: Class[T])
      extends SocketOption[T] {
    override def toString = name
  }
}
