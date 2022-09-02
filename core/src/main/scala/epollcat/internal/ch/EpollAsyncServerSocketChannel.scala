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

import epollcat.unsafe.EpollExecutorScheduler
import epollcat.unsafe.EpollRuntime

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
import scala.annotation.nowarn
import scala.scalanative.annotation.stub
import scala.scalanative.libc.errno
import scala.scalanative.posix
import scala.scalanative.posix.netdbOps._
import scala.scalanative.unsafe._

final class EpollAsyncServerSocketChannel private (fd: Int)
    extends AsynchronousServerSocketChannel(null) {

  private var ctlDel: Runnable = null

  private[this] var _isOpen: Boolean = true
  private[this] var readReady: Boolean = false
  private[this] var readCallback: Runnable = null

  private def callback(events: Int): Unit = {
    if ((events & EpollExecutorScheduler.Read) != 0) {
      readReady = true
      if (readCallback != null) readCallback.run()
    }
  }

  def close(): Unit = {
    _isOpen = false
    ctlDel.run()
    if (posix.unistd.close(fd) == -1)
      throw new IOException(s"close: ${errno.errno}")
  }

  def isOpen = _isOpen

  @stub
  def getOption[T](name: SocketOption[T]): T = ???

  def bind(local: SocketAddress, backlog: Int): AsynchronousServerSocketChannel = {
    val addrinfo = stackalloc[Ptr[posix.netdb.addrinfo]]()
    Zone { implicit z =>
      val addr = local.asInstanceOf[InetSocketAddress]
      val hints = stackalloc[posix.netdb.addrinfo]()
      hints.ai_family = posix.sys.socket.AF_INET
      hints.ai_flags = posix.netdb.AI_NUMERICHOST | posix.netdb.AI_NUMERICSERV
      hints.ai_socktype = posix.sys.socket.SOCK_STREAM
      val rtn = posix
        .netdb
        .getaddrinfo(
          toCString(addr.getAddress().getHostAddress()),
          toCString(addr.getPort.toString),
          hints,
          addrinfo
        )
      if (rtn != 0)
        throw new IOException(s"getaddrinfo: ${rtn}")
    }

    val bindRet = posix.sys.socket.bind(fd, (!addrinfo).ai_addr, (!addrinfo).ai_addrlen)
    posix.netdb.freeaddrinfo(!addrinfo)
    if (bindRet == -1) errno.errno match {
      case e if e == posix.errno.EADDRINUSE =>
        throw new BindException("Address already in use")
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
      val clientFd = syssocket.accept4(fd, null, null, EpollAsyncSocketChannel.SOCK_NONBLOCK)
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
        handler.completed(EpollAsyncSocketChannel.open(clientFd), attachment)
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
  private final val SOCK_NONBLOCK = 2048

  def open(): EpollAsyncServerSocketChannel = {
    EpollRuntime.global.compute match {
      case epoll: EpollExecutorScheduler =>
        val fd = posix
          .sys
          .socket
          .socket(posix.sys.socket.AF_INET, posix.sys.socket.SOCK_STREAM | SOCK_NONBLOCK, 0)
        if (fd == -1)
          throw new RuntimeException(s"socket: ${errno.errno}")
        val ch = new EpollAsyncServerSocketChannel(fd)
        ch.ctlDel =
          epoll.ctl(fd, EpollExecutorScheduler.Read | EpollExecutorScheduler.EdgeTriggered)(
            ch.callback(_)
          )
        ch
      case _ => throw new RuntimeException("Global compute is not an EpollExecutorScheduler!")
    }
  }
}

@extern
@nowarn
private[ch] object syssocket {
  def accept4(sockfd: CInt, addr: Ptr[Byte], addrlen: Ptr[Byte], flags: CInt): CInt = extern
}
