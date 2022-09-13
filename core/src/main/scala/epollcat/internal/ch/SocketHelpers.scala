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
import java.nio.channels.UnsupportedAddressTypeException
import scala.scalanative.libc.errno
import scala.scalanative.meta.LinktimeInfo
import scala.scalanative.posix
import scala.scalanative.posix.netdbOps._
import scala.scalanative.posix.netinet.inOps._
import scala.scalanative.unsafe._

private[ch] object SocketHelpers {

  lazy val preferIPv4Stack =
    java.lang.Boolean.parseBoolean(System.getProperty("java.net.preferIPv4Stack", "false"))

  def mkNonBlocking(): CInt = {
    val SOCK_NONBLOCK =
      if (LinktimeInfo.isLinux)
        socket.SOCK_NONBLOCK
      else 0

    val domain =
      if (preferIPv4Stack)
        posix.sys.socket.AF_INET
      else
        posix.sys.socket.AF_INET6

    val fd = posix.sys.socket.socket(domain, posix.sys.socket.SOCK_STREAM | SOCK_NONBLOCK, 0)

    if (fd == -1)
      throw new RuntimeException(s"socket: ${errno.errno}")

    if (!LinktimeInfo.isLinux) setNonBlocking(fd)

    fd
  }

  def setNonBlocking(fd: CInt): Unit =
    if (posix.fcntl.fcntl(fd, posix.fcntl.F_SETFL, posix.fcntl.O_NONBLOCK) != 0)
      throw new IOException(s"fcntl: ${errno.errno}")
    else ()

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
    val addr = // allocate enough for an IPv6
      stackalloc[posix.netinet.in.sockaddr_in6]().asInstanceOf[Ptr[posix.sys.socket.sockaddr]]
    val len = stackalloc[posix.sys.socket.socklen_t]()
    !len = sizeof[posix.netinet.in.sockaddr_in6].toUInt
    if (posix.sys.socket.getsockname(fd, addr, len) == -1)
      throw new IOException(s"getsockname: ${errno.errno}")
    if (preferIPv4Stack)
      toInet4SocketAddress(addr.asInstanceOf[Ptr[posix.netinet.in.sockaddr_in]])
    else
      toInet6SocketAddress(addr.asInstanceOf[Ptr[posix.netinet.in.sockaddr_in6]])
  }

  def toInet4SocketAddress(
      addr: Ptr[posix.netinet.in.sockaddr_in]
  ): InetSocketAddress = {
    val port = posix.arpa.inet.htons(addr.sin_port).toInt
    val addrBytes = addr.sin_addr.at1.asInstanceOf[Ptr[Byte]]
    val inetAddr = InetAddress.getByAddress(
      Array(addrBytes(0), addrBytes(1), addrBytes(2), addrBytes(3))
    )
    new InetSocketAddress(inetAddr, port)
  }

  def toInet6SocketAddress(
      addr: Ptr[posix.netinet.in.sockaddr_in6]
  ): InetSocketAddress = {
    val port = posix.arpa.inet.htons(addr.sin6_port).toInt
    val addrBytes = addr.sin6_addr.at1.asInstanceOf[Ptr[Byte]]
    val inetAddr = InetAddress.getByAddress {
      val addr = new Array[Byte](16)
      var i = 0
      while (i < addr.length) {
        addr(i) = addrBytes(i.toLong)
        i += 1
      }
      addr
    }
    new InetSocketAddress(inetAddr, port)
  }

  def toAddrinfo(addr: InetSocketAddress): Either[Throwable, Ptr[posix.netdb.addrinfo]] = Zone {
    implicit z =>
      val addrinfo = stackalloc[Ptr[posix.netdb.addrinfo]]()
      val hints = stackalloc[posix.netdb.addrinfo]()
      hints.ai_family =
        if (preferIPv4Stack)
          posix.sys.socket.AF_INET
        else
          posix.sys.socket.AF_INET6
      hints.ai_flags = posix.netdb.AI_NUMERICHOST | posix.netdb.AI_NUMERICSERV
      if (!preferIPv4Stack) hints.ai_flags |= posix.netdb.AI_V4MAPPED
      hints.ai_socktype = posix.sys.socket.SOCK_STREAM
      val rtn = posix
        .netdb
        .getaddrinfo(
          toCString(addr.getAddress().getHostAddress()),
          toCString(addr.getPort.toString),
          hints,
          addrinfo
        )
      if (rtn == 0) {
        Right(!addrinfo)
      } else {
        val ex = if (rtn == posix.netdb.EAI_FAMILY) {
          new UnsupportedAddressTypeException()
        } else {
          val msg = s"getaddrinfo: ${SocketHelpers.getGaiErrorMessage(rtn)}"
          new IOException(msg)
        }
        Left(ex)
      }
  }

  // Return text translation of getaddrinfo (gai) error code.
  def getGaiErrorMessage(gaiErrorCode: CInt): String = {
    fromCString(posix.netdb.gai_strerror(gaiErrorCode))
  }

}
