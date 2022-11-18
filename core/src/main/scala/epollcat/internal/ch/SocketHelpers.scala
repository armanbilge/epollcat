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

  /* An approach with better software engineering becomes available
   * when epollcat no longer supports Scala Native 0.4.n.
   * One can eliminate the chance of races & mismatches by allocating a
   * Scala Native socket and examining its address family. Scala Native
   * will have done all the work.
   *
   * Tcp6Suite.scala uses a similar technique querying an
   * AsynchronousServerSocketChannel.
   */

  lazy val useIPv4Stack = {
    val systemPropertyForcesIPv4 =
      java.lang.Boolean.parseBoolean(System.getProperty("java.net.preferIPv4Stack", "false"))
    systemPropertyForcesIPv4 || hasOnlyIPv4Stack()
  }

  private def hasOnlyIPv4Stack(): Boolean = {
    val addrinfo = stackalloc[Ptr[posix.netdb.addrinfo]]()
    val hints = stackalloc[posix.netdb.addrinfo]()

    hints.ai_family = posix.sys.socket.AF_INET6
    hints.ai_flags = posix.netdb.AI_NUMERICHOST | posix.netdb.AI_NUMERICSERV
    hints.ai_flags |= posix.netdb.AI_PASSIVE // ServerSocket
    hints.ai_flags |= posix.netdb.AI_ADDRCONFIG
    hints.ai_socktype = posix.sys.socket.SOCK_STREAM

    val typelevelOrg6 = c"2606:50c0:8003::153"

    val rtn = posix
      .netdb
      .getaddrinfo(
        typelevelOrg6,
        c"0",
        hints,
        addrinfo
      )

    val hasIPv6 =
      try {
        if (rtn == 0) {
          // should never happen, but check anyways
          java.util.Objects.requireNonNull(!addrinfo)
          (!addrinfo).ai_family == posix.sys.socket.AF_INET6
        } else {

          if (rtn == posix.netdb.EAI_NONAME) { // expected on IPv4 & OK
            false
          } else if (rtn == posix.netdb.EAI_FAMILY) { // no IPv6 stack at all
            false
          } else {
            val EAI_ADDRFAMILY =
              if (LinktimeInfo.isLinux) -9
              else if (LinktimeInfo.isFreeBSD) 1 // from FreeBSD source, untested
              else {
                // EAI_ADDRFAMILY is not defined on macOS & others.
                // Force mismatch & allow throw, Exception has info we want to see.
                0
              }

            if (rtn == EAI_ADDRFAMILY) {
              false
            } else {
              val msg =
                s"getaddrinfo: ${SocketHelpers.getGaiErrorMessage(rtn)}"
              throw new IOException(msg)
            }
          }
        }
      } finally {
        posix.netdb.freeaddrinfo(!addrinfo)
      }

    !hasIPv6
  }

  def mkNonBlocking(): CInt = {
    val SOCK_NONBLOCK =
      if (LinktimeInfo.isLinux)
        socket.SOCK_NONBLOCK
      else 0

    val domain =
      if (useIPv4Stack)
        posix.sys.socket.AF_INET
      else
        posix.sys.socket.AF_INET6

    val fd = posix.sys.socket.socket(domain, posix.sys.socket.SOCK_STREAM | SOCK_NONBLOCK, 0)

    if (fd == -1)
      throw new RuntimeException(s"socket: ${errno.errno}")

    if (!LinktimeInfo.isLinux) setNonBlocking(fd)
    if (LinktimeInfo.isMac) setNoSigPipe(fd)

    fd
  }

  def setNonBlocking(fd: CInt): Unit =
    if (posix.fcntl.fcntl(fd, posix.fcntl.F_SETFL, posix.fcntl.O_NONBLOCK) != 0)
      throw new IOException(s"fcntl: ${errno.errno}")
    else ()

  // macOS-only
  def setNoSigPipe(fd: CInt): Unit =
    setOption(fd, socket.SO_NOSIGPIPE, true)

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

  /* On macOS, 11 & 12, Intel & Mx, getsockname() appears to return
   * an IPv4 compatible IPv6 address, such as ::127.0.0.1 where a
   * contemporary developer would expect an IPv4 mapped IPv6 address
   * (::FFFF:127.0.0.1). This tends to happen on the first call after
   * an executable file has started. Additional calls are the expected
   * IPv4 mapped address.
   *
   * For consistent handling both within macOS and across differing
   * operating systems, always convert IPv4 compatible to IPv4 mapped
   * IPv6 addresses.
   *
   * To understand the conversion check the RFCs,
   * https://www.rfc-editor.org/rfc/rfc4291.html and related.
   */
  private def forceIPv4CompatibleToMapped(sa6: Ptr[posix.netinet.in.sockaddr_in6]): Unit = {
    def needsConversion(pb: Ptr[Byte]): Boolean = {
      // Let no pointer go un-munged.
      val ptrInt = pb.asInstanceOf[Ptr[Int]]
      val ptrLong = pb.asInstanceOf[Ptr[Long]]
      val isIPv4Compatible = (ptrInt(2) == 0x00) && (ptrLong(0) == 0x0L)

      if (!isIPv4Compatible) false
      else ptrLong(1) > 0x100000000000000L // skip IPv6 'any' or loopback
    }

    val addrBytes = sa6.sin6_addr.at1.at(0).asInstanceOf[Ptr[Byte]]

    if (needsConversion(addrBytes)) {
      addrBytes(10) = 0xff.toByte
      addrBytes(11) = 0xff.toByte
    }
  }

  def getLocalAddress(fd: CInt): SocketAddress = {
    val addr = // allocate enough for an IPv6
      stackalloc[posix.netinet.in.sockaddr_in6]().asInstanceOf[Ptr[posix.sys.socket.sockaddr]]
    val len = stackalloc[posix.sys.socket.socklen_t]()
    !len = sizeof[posix.netinet.in.sockaddr_in6].toUInt
    if (posix.sys.socket.getsockname(fd, addr, len) == -1)
      throw new IOException(s"getsockname: ${errno.errno}")
    if (useIPv4Stack)
      toInet4SocketAddress(addr.asInstanceOf[Ptr[posix.netinet.in.sockaddr_in]])
    else {
      val sa6 = addr.asInstanceOf[Ptr[posix.netinet.in.sockaddr_in6]]
      if (LinktimeInfo.isMac)
        forceIPv4CompatibleToMapped(sa6) // see comments at method

      toInet6SocketAddress(sa6)
    }
  }

  def toInet4SocketAddress(
      addr: Ptr[posix.netinet.in.sockaddr_in]
  ): InetSocketAddress = {
    val port = posix.arpa.inet.ntohs(addr.sin_port).toInt
    val addrBytes = addr.sin_addr.at1.asInstanceOf[Ptr[Byte]]
    val inetAddr = InetAddress.getByAddress(
      Array(addrBytes(0), addrBytes(1), addrBytes(2), addrBytes(3))
    )
    new InetSocketAddress(inetAddr, port)
  }

  def toInet6SocketAddress(
      addr: Ptr[posix.netinet.in.sockaddr_in6]
  ): InetSocketAddress = {
    val port = posix.arpa.inet.ntohs(addr.sin6_port).toInt
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
        if (useIPv4Stack)
          posix.sys.socket.AF_INET
        else
          posix.sys.socket.AF_INET6
      hints.ai_flags = posix.netdb.AI_NUMERICHOST | posix.netdb.AI_NUMERICSERV
      if (!useIPv4Stack) hints.ai_flags |= posix.netdb.AI_V4MAPPED
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
