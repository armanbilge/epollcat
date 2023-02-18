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

import epollcat.unsafe.EpollRuntime
import epollcat.unsafe.EventNotificationCallback
import epollcat.unsafe.EventPollingExecutorScheduler

import java.io.IOException
import java.net.BindException
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.SocketOption
import java.net.StandardSocketOptions
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.concurrent.Future
import scala.scalanative.annotation.stub
import scala.scalanative.libc.errno
import scala.scalanative.meta.LinktimeInfo
import scala.scalanative.posix
import scala.scalanative.posix.netdbOps._
import scala.scalanative.unsafe._
import scala.util.control.NonFatal

import socket._

final class EpollAsyncServerSocketChannel private (fd: Int)
    extends AsynchronousServerSocketChannel(null)
    with EventNotificationCallback {

  private var unmonitor: Runnable = null

  private[this] var _isOpen: Boolean = true
  private[this] var readReady: Boolean = false
  private[this] var readCallback: Runnable = null

  protected[epollcat] def notifyEvents(readReady: Boolean, writeReady: Boolean): Unit = {
    if (readReady) {
      this.readReady = true
      if (readCallback != null) readCallback.run()
    }
  }

  def close(): Unit = if (isOpen()) {
    _isOpen = false
    unmonitor.run()
    if (posix.unistd.close(fd) == -1)
      throw new IOException(s"close: ${errno.errno}")
  }

  def isOpen = _isOpen

  @stub
  def getOption[T](name: SocketOption[T]): T = ???

  def bind(localArg: SocketAddress, backlog: Int): AsynchronousServerSocketChannel = {

    /* JVM defacto practice of null becoming wildcard.
     *  on IPv6 systems, 0.0.0.0 will get converted to ::0.
     */
    val local =
      if (localArg != null) localArg
      else new InetSocketAddress("0.0.0.0", 0)

    val addrinfo = SocketHelpers.toAddrinfo(local.asInstanceOf[InetSocketAddress]) match {
      case Left(ex) => throw ex
      case Right(addrinfo) => addrinfo
    }

    val bindRet = posix.sys.socket.bind(fd, addrinfo.ai_addr, addrinfo.ai_addrlen)
    posix.netdb.freeaddrinfo(addrinfo)

    // posix.errno.EADDRNOTAVAIL becomes available in Scala Native 0.5.0
    val EADDRNOTAVAIL =
      if (LinktimeInfo.isLinux) 99
      else if (LinktimeInfo.isMac) 49
      else Int.MaxValue // punt, will never match an errno.

    if (bindRet == -1) errno.errno match {
      case e if e == posix.errno.EADDRINUSE =>
        throw new BindException("Address already in use")
      case e if e == EADDRNOTAVAIL =>
        // Whis code may have to change when support for a new OS is added.
        if (LinktimeInfo.isMac)
          throw new BindException("Can't assign requested address")
        else // Linux & a bet that an unknownd OS uses good grammer.
          throw new BindException("Cannot assign requested address")
      case other => throw new IOException(s"bind: $other")
    }

    if (posix.sys.socket.listen(fd, backlog) == -1)
      throw new IOException(s"listen: ${errno.errno}")

    this
  }

  def setOption[T](name: SocketOption[T], value: T): AsynchronousServerSocketChannel =
    name match {
      case StandardSocketOptions.SO_RCVBUF =>
        SocketHelpers.setOption(
          fd,
          posix.sys.socket.SO_RCVBUF,
          value.asInstanceOf[java.lang.Integer]
        )
        this
      case StandardSocketOptions.SO_REUSEADDR =>
        SocketHelpers.setOption(
          fd,
          posix.sys.socket.SO_REUSEADDR,
          value.asInstanceOf[java.lang.Boolean]
        )
        this
      case StandardSocketOptions.SO_REUSEPORT =>
        SocketHelpers.setOption(
          fd,
          posix.sys.socket.SO_REUSEPORT,
          value.asInstanceOf[java.lang.Boolean]
        )
        this
      case _ => throw new IllegalArgumentException
    }

  def accept[A](
      attachment: A,
      handler: CompletionHandler[AsynchronousSocketChannel, _ >: A]
  ): Unit = {
    if (readReady) {
      val addr = // allocate enough for an IPv6
        stackalloc[posix.netinet.in.sockaddr_in6]().asInstanceOf[Ptr[posix.sys.socket.sockaddr]]
      val addrlen = stackalloc[posix.sys.socket.socklen_t]()
      !addrlen = sizeof[posix.netinet.in.sockaddr_in6].toUInt
      val clientFd =
        if (LinktimeInfo.isLinux)
          socket.accept4(
            fd,
            addr.asInstanceOf[Ptr[posix.sys.socket.sockaddr]],
            addrlen,
            SOCK_NONBLOCK
          )
        else {
          posix.sys.socket.accept(fd, addr, addrlen)
        }
      if (clientFd == -1) {
        if (errno.errno == posix.errno.EAGAIN || errno.errno == posix.errno.EWOULDBLOCK) {
          readReady = false
          readCallback = () => {
            readCallback = null
            accept(attachment, handler)
          }
        } else {
          handler.failed(new IOException(s"accept: ${errno.errno}"), attachment)
        }
      } else {
        try {
          if (!LinktimeInfo.isLinux)
            SocketHelpers.setNonBlocking(clientFd)
          val inetAddr =
            if (SocketHelpers.useIPv4Stack)
              SocketHelpers.toInet4SocketAddress(
                addr.asInstanceOf[Ptr[posix.netinet.in.sockaddr_in]]
              )
            else
              SocketHelpers.toInet6SocketAddress(
                addr.asInstanceOf[Ptr[posix.netinet.in.sockaddr_in6]]
              )
          val ch = EpollAsyncSocketChannel(clientFd, inetAddr)
          handler.completed(ch, attachment)
        } catch {
          case NonFatal(ex) =>
            handler.failed(ex, attachment)
        }
      }
    } else {
      readCallback = () => {
        readCallback = null
        accept(attachment, handler)
      }
    }
  }

  @stub
  def accept(): Future[AsynchronousSocketChannel] = ???

  def getLocalAddress(): SocketAddress = SocketHelpers.getLocalAddress(fd)

  @stub
  def supportedOptions(): java.util.Set[SocketOption[_]] = ???

}

object EpollAsyncServerSocketChannel {

  def open(): EpollAsyncServerSocketChannel = {
    EpollRuntime.global.compute match {
      case epoll: EventPollingExecutorScheduler =>
        val fd = SocketHelpers.mkNonBlocking()
        val ch = new EpollAsyncServerSocketChannel(fd)
        ch.setOption(StandardSocketOptions.SO_REUSEADDR, java.lang.Boolean.TRUE)
        ch.unmonitor = epoll.monitor(fd, reads = true, writes = false)(ch)
        ch
      case _ =>
        throw new RuntimeException("Global compute is not an EventPollingExecutorScheduler")
    }
  }
}
