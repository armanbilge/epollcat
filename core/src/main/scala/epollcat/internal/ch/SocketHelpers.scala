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

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import scala.scalanative.libc.errno
import scala.scalanative.meta.LinktimeInfo
import scala.scalanative.posix
import scala.scalanative.posix.netinet.inOps._
import scala.scalanative.unsafe._

private[ch] object SocketHelpers {

  def mkNonBlocking(): CInt = {
    val SOCK_NONBLOCK =
      if (LinktimeInfo.isLinux)
        socket.SOCK_NONBLOCK
      else 0

    val fd = posix
      .sys
      .socket
      .socket(posix.sys.socket.AF_INET, posix.sys.socket.SOCK_STREAM | SOCK_NONBLOCK, 0)

    if (fd == -1)
      throw new RuntimeException(s"socket: ${errno.errno}")

    setNonBlocking(fd)

    fd
  }

  def setNonBlocking(fd: CInt): Unit = if (!LinktimeInfo.isLinux) {
    if (posix.fcntl.fcntl(fd, posix.fcntl.F_SETFL, posix.fcntl.O_NONBLOCK) != 0)
      throw new IOException(s"fcntl: ${errno.errno}")
  }

  def setOption(fd: CInt, option: CInt, value: Boolean): Unit = {
    val ptr = stackalloc[CInt]()
    !ptr = if (value.asInstanceOf[java.lang.Boolean]) 1 else 0
    if (posix
        .sys
        .socket
        .setsockopt(
          fd,
          posix.sys.socket.SOL_SOCKET,
          option,
          ptr.asInstanceOf[Ptr[Byte]],
          sizeof[CInt].toUInt) == -1)
      throw new IOException(s"setsockopt: ${errno.errno}")
  }

  def setTcpOption(fd: CInt, option: CInt, value: Boolean): Unit = {
    val ptr = stackalloc[CInt]()
    !ptr = if (value.asInstanceOf[java.lang.Boolean]) 1 else 0
    if (posix
        .sys
        .socket
        .setsockopt(
          fd,
          posix.netinet.in.IPPROTO_TCP, // aka SOL_TCP
          option,
          ptr.asInstanceOf[Ptr[Byte]],
          sizeof[CInt].toUInt) == -1)
      throw new IOException(s"setsockopt: ${errno.errno}")
  }

  def setOption(fd: CInt, option: CInt, value: Int): Unit = {
    val ptr = stackalloc[CInt]()
    !ptr = value
    if (posix
        .sys
        .socket
        .setsockopt(
          fd,
          posix.sys.socket.SOL_SOCKET,
          option,
          ptr.asInstanceOf[Ptr[Byte]],
          sizeof[CInt].toUInt) == -1)
      throw new IOException(s"setsockopt: ${errno.errno}")
  }

  def getLocalAddress(fd: CInt): SocketAddress = {
    val addr = stackalloc[posix.netinet.in.sockaddr_in]()
    val len = stackalloc[posix.sys.socket.socklen_t]()
    !len = sizeof[posix.sys.socket.sockaddr].toUInt
    if (posix
        .sys
        .socket
        .getsockname(fd, addr.asInstanceOf[Ptr[posix.sys.socket.sockaddr]], len) == -1)
      throw new IOException(s"getsockname: ${errno.errno}")
    val port = posix.arpa.inet.htons(addr.sin_port).toInt
    val addrBytes = addr.sin_addr.at1.asInstanceOf[Ptr[Byte]]
    val inetAddr = InetAddress.getByAddress(
      Array(addrBytes(0), addrBytes(1), addrBytes(2), addrBytes(3))
    )
    new InetSocketAddress(inetAddr, port)
  }

}
