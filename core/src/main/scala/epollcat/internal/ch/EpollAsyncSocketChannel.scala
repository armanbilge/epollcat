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
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.SocketOption
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.ClosedChannelException
import java.nio.channels.CompletionHandler
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import scala.annotation.tailrec
import scala.scalanative.annotation.stub
import scala.scalanative.libc.errno
import scala.scalanative.meta.LinktimeInfo
import scala.scalanative.posix
import scala.scalanative.posix.netdbOps._
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

final class EpollAsyncSocketChannel private (
    fd: Int,
    private[this] var remoteAddress: SocketAddress
) extends AsynchronousSocketChannel(null)
    with EventNotificationCallback {

  private var unmonitor: Runnable = null

  private[this] var _isOpen: Boolean = true
  private[this] var outputShutdown: Boolean = false
  private[this] var readReady: Boolean = false
  private[this] var readCallback: Runnable = null
  private[this] var writeReady: Boolean = false
  private[this] var writeCallback: Runnable = null

  protected[epollcat] def notifyEvents(readReady: Boolean, writeReady: Boolean): Unit = {
    if (readReady) {
      this.readReady = true
      if (readCallback != null) readCallback.run()
    }

    if (writeReady) {
      this.writeReady = true
      if (writeCallback != null) writeCallback.run()
    }
  }

  def close(): Unit = if (isOpen()) {
    _isOpen = false
    unmonitor.run()
    if (posix.unistd.close(fd) == -1)
      throw new IOException(s"close: ${errno.errno}")
  }

  def isOpen = _isOpen

  def shutdownInput(): AsynchronousSocketChannel = {
    if (posix.sys.socket.shutdown(fd, 0) == -1)
      throw new IOException(s"shutdown: ${errno.errno}")
    this
  }

  def shutdownOutput(): AsynchronousSocketChannel = {
    outputShutdown = true
    if (posix.sys.socket.shutdown(fd, 1) == -1)
      throw new IOException(s"shutdown: ${errno.errno}")
    this
  }

  def getRemoteAddress(): SocketAddress = remoteAddress

  @stub
  def read[A](
      dsts: Array[ByteBuffer],
      offset: Int,
      length: Int,
      timeout: Long,
      unit: TimeUnit,
      attachment: A,
      handler: CompletionHandler[java.lang.Long, _ >: A]
  ): Unit = ???

  @stub
  def read(dst: ByteBuffer): Future[Integer] = ???

  def read[A](
      dst: ByteBuffer,
      timeout: Long,
      unit: TimeUnit,
      attachment: A,
      handler: CompletionHandler[Integer, _ >: A]
  ): Unit =
    if (readReady) {
      val position = dst.position()
      val count = dst.remaining()
      val hasArray = dst.hasArray()
      val buf = if (hasArray) dst.array() else new Array[Byte](count)
      val offset = if (hasArray) dst.arrayOffset() + position else 0

      def completed(total: Int): Unit = {
        if (hasArray)
          dst.position(position + total)
        else
          dst.put(buf, 0, total)
        handler.completed(total, attachment)
      }

      @tailrec
      def go(buf: Ptr[Byte], count: Int, total: Int): Unit = {
        val readed = posix.unistd.read(fd, buf, count.toULong)
        if (readed == -1) {
          val e = errno.errno
          if (e == posix.errno.EAGAIN || e == posix.errno.EWOULDBLOCK) {
            readReady = false
            completed(total)
          } else
            handler.failed(new RuntimeException(s"read: $e"), attachment)
        } else if (readed == 0) {
          if (total > 0)
            completed(total)
          else
            handler.completed(-1, attachment)
        } else if (readed < count)
          go(buf + readed.toLong, count - readed, total + readed)
        else // readed == count
          completed(total + readed)
      }

      go(buf.at(offset), count, 0)
    } else {
      readCallback = () => {
        readCallback = null
        read(dst, timeout, unit, attachment, handler)
      }
    }

  @stub
  def connect(remote: SocketAddress): Future[Void] = ???

  def connect[A](
      remoteAddr: SocketAddress,
      attachment: A,
      handler: CompletionHandler[Void, _ >: A]
  ): Unit = {
    // See epollcat Issue #92 for a complete discussion of macOS "feature".
    def forceMacWildcardToLoopback(sa: SocketAddress): SocketAddress = {
      val isa = sa.asInstanceOf[InetSocketAddress]
      val bytes = isa.getAddress().getAddress()
      if ((bytes.length != 16) || (bytes.indexWhere(_ != 0) >= 0)) sa
      else
        new InetSocketAddress(InetAddress.getLoopbackAddress(), isa.getPort())
    }

    val remote =
      if (!LinktimeInfo.isMac) remoteAddr
      else forceMacWildcardToLoopback(remoteAddr)

    val addrinfo = SocketHelpers.toAddrinfo(remote.asInstanceOf[InetSocketAddress]) match {
      case Left(ex) =>
        return handler.failed(ex, attachment)
      case Right(addrinfo) => addrinfo
    }

    val conRet = posix.sys.socket.connect(fd, addrinfo.ai_addr, addrinfo.ai_addrlen)
    posix.netdb.freeaddrinfo(addrinfo)

    if (conRet == -1 && errno.errno != posix.errno.EINPROGRESS) {
      val ex = errno.errno match {
        case e if e == posix.errno.ECONNREFUSED =>
          new ConnectException("Connection refused")
        case other => new IOException(s"connect: $other")
      }
      return handler.failed(ex, attachment)
    }

    val callback: Runnable = () => {
      writeCallback = null
      val optval = stackalloc[CInt]()
      val optlen = stackalloc[posix.sys.socket.socklen_t]()
      !optlen = sizeof[CInt].toUInt
      if (posix
          .sys
          .socket
          .getsockopt(
            fd,
            posix.sys.socket.SOL_SOCKET,
            posix.sys.socket.SO_ERROR,
            optval.asInstanceOf[Ptr[Byte]],
            optlen
          ) == -1)
        return handler.failed(new IOException(s"getsockopt: ${errno.errno}"), attachment)

      if (!optval == 0) {
        remoteAddress = remote
        handler.completed(null, attachment)
      } else {
        val ex = !optval match {
          case e if e == posix.errno.ECONNREFUSED =>
            new ConnectException("Connection refused")
          case other => new IOException(s"connect: $other")
        }
        handler.failed(ex, attachment)
      }
    }

    if (writeReady)
      callback.run()
    else
      writeCallback = callback
  }

  @stub
  def getOption[T](name: SocketOption[T]): T = ???

  @stub
  def bind(local: SocketAddress): AsynchronousSocketChannel = ???

  def setOption[T](name: SocketOption[T], value: T): AsynchronousSocketChannel = name match {
    case StandardSocketOptions.SO_SNDBUF =>
      SocketHelpers.setOption(
        fd,
        posix.sys.socket.SO_SNDBUF,
        value.asInstanceOf[java.lang.Integer]
      )
      this
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
    case StandardSocketOptions.SO_KEEPALIVE =>
      SocketHelpers.setOption(
        fd,
        posix.sys.socket.SO_KEEPALIVE,
        value.asInstanceOf[java.lang.Boolean]
      )
      this
    case StandardSocketOptions.TCP_NODELAY =>
      SocketHelpers.setTcpOption(
        fd,
        posix.netinet.tcp.TCP_NODELAY,
        value.asInstanceOf[java.lang.Boolean]
      )
      this
    case _ => throw new IllegalArgumentException
  }

  @stub
  def write[A](
      srcs: Array[ByteBuffer],
      offset: Int,
      length: Int,
      timeout: Long,
      unit: TimeUnit,
      attachment: A,
      handler: CompletionHandler[java.lang.Long, _ >: A]
  ): Unit = ???

  @stub
  def write(src: ByteBuffer): Future[Integer] = ???

  def write[A](
      src: ByteBuffer,
      timeout: Long,
      unit: TimeUnit,
      attachment: A,
      handler: CompletionHandler[Integer, _ >: A]
  ): Unit = if (outputShutdown)
    handler.failed(new ClosedChannelException, attachment)
  else if (writeReady) {
    val position = src.position()
    val count = src.remaining()

    val hasArray = src.hasArray()
    val buf =
      if (hasArray) src.array()
      else {
        val buf = new Array[Byte](count)
        src.get(buf)
        buf
      }
    val offset = if (hasArray) src.arrayOffset() + position else 0

    def completed(total: Int): Unit = {
      src.position(position + total)
      handler.completed(total, attachment)
    }

    @tailrec
    def go(buf: Ptr[Byte], count: Int, total: Int): Unit = {
      val wrote =
        if (LinktimeInfo.isLinux)
          posix.sys.socket.send(fd, buf, count.toULong, socket.MSG_NOSIGNAL).toInt
        else
          posix.unistd.write(fd, buf, count.toULong)

      if (wrote == -1) {
        val e = errno.errno
        if (e == posix.errno.EAGAIN || e == posix.errno.EWOULDBLOCK) {
          writeReady = false
          completed(total)
        } else
          handler.failed(new RuntimeException(s"write: $e"), attachment)
      } else if (wrote < count)
        go(buf + wrote.toLong, count - wrote, total + wrote)
      else // wrote == count
        completed(total + wrote)
    }

    go(buf.at(offset), count, 0)
  } else {
    writeCallback = () => {
      writeCallback = null
      write(src, timeout, unit, attachment, handler)
    }
  }

  def getLocalAddress(): SocketAddress = SocketHelpers.getLocalAddress(fd)

  @stub
  def supportedOptions(): java.util.Set[SocketOption[_]] = ???

}

object EpollAsyncSocketChannel {

  def open(): EpollAsyncSocketChannel = {
    val fd = SocketHelpers.mkNonBlocking()
    apply(fd, null)
  }

  private[ch] def apply(fd: CInt, remoteAddress: SocketAddress): EpollAsyncSocketChannel = {
    EpollRuntime.global.compute match {
      case epoll: EventPollingExecutorScheduler =>
        val ch = new EpollAsyncSocketChannel(fd, remoteAddress)
        ch.unmonitor = epoll.monitor(fd, reads = true, writes = true)(ch)
        ch
      case _ =>
        throw new RuntimeException("Global compute is not an EventPollingExecutorScheduler")
    }
  }
}
