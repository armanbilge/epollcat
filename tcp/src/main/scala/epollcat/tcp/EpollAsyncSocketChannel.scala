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

package epollcat.tcp

import epollcat.unsafe.EpollExecutorScheduler
import epollcat.unsafe.EpollRuntime

import java.io.IOException
import java.net.SocketAddress
import java.net.SocketOption
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import scala.annotation.tailrec
import scala.scalanative.annotation.stub
import scala.scalanative.libc.errno
import scala.scalanative.posix
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

final class EpollAsyncSocketChannel private (fd: Int) extends AsynchronousSocketChannel(null) {

  private var ctlDel: Runnable = null

  private[this] var _isOpen: Boolean = true
  private[this] var readReady: Boolean = false
  private[this] var readCallback: Runnable = null
  private[this] var writeReady: Boolean = false
  private[this] var writeCallback: Runnable = null

  private def callback(events: Int): Unit = {
    if ((events & EpollExecutorScheduler.Read) != 0) {
      readReady = true
      if (readCallback != null) readCallback.run()
    }

    if ((events & EpollExecutorScheduler.Write) != 0) {
      writeReady = true
      if (writeCallback != null) writeCallback.run()
    }
  }

  def close(): Unit = {
    _isOpen = false
    ctlDel.run()
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
    if (posix.sys.socket.shutdown(fd, 1) == -1)
      throw new IOException(s"shutdown: ${errno.errno}")
    this
  }

  def getRemoteAddress(): SocketAddress = ???

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
      Zone { implicit z =>
        val count = dst.remaining()
        val buf = alloc[Byte](count.toLong)

        def completed(total: Int): Unit = {
          var i = 0
          while (i < total) {
            dst.put(i, buf(i.toLong))
            i += 1
          }
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
          } else if (readed < count)
            go(buf + readed.toLong, count - readed, total + readed)
          else // readed == count
            completed(total + readed)
        }

        go(buf, count, 0)
      }
    } else {
      readCallback = () => {
        readCallback = null
        read(dst, timeout, unit, attachment, handler)
      }
    }

  @stub
  def connect(remote: SocketAddress): Future[Void] = ???

  def connect[A](
      remote: SocketAddress,
      attachment: A,
      handler: CompletionHandler[Void, _ >: A]
  ): Unit = ???

  @stub
  def getOption[T](name: SocketOption[T]): T = ???

  @stub
  def bind(local: SocketAddress): AsynchronousSocketChannel = ???

  def setOption[T](name: SocketOption[T], value: T): AsynchronousSocketChannel =
    throw new UnsupportedOperationException

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
  ): Unit = if (writeReady) {
    Zone { implicit z =>
      val count = src.remaining()
      val buf = alloc[Byte](count.toLong)
      var i = 0
      while (i < count) {
        buf(i.toLong) = src.get(i)
        i += 1
      }

      @tailrec
      def go(buf: Ptr[Byte], count: Int, total: Int): Unit = {
        val wrote = posix.unistd.write(fd, buf, count.toULong)
        if (wrote == -1) {
          val e = errno.errno
          if (e == posix.errno.EAGAIN || e == posix.errno.EWOULDBLOCK) {
            writeReady = false
            handler.completed(total, attachment)
          } else
            handler.failed(new RuntimeException(s"write: $e"), attachment)
        } else if (wrote < count)
          go(buf + wrote.toLong, count - wrote, total + wrote)
        else // wrote == count
          handler.completed(total + wrote, attachment)
      }

      go(buf, count, 0)
    }
  } else {
    writeCallback = () => {
      writeCallback = null
      write(src, timeout, unit, attachment, handler)
    }
  }

  def getLocalAddress(): SocketAddress = ???

  @stub
  def supportedOptions(): java.util.Set[SocketOption[_]] = ???

}

object EpollAsyncSocketChannel {
  private final val SOCK_NONBLOCK = 2048

  def open(): EpollAsyncSocketChannel = {
    EpollRuntime.global.compute match {
      case epoll: EpollExecutorScheduler =>
        val fd = posix
          .sys
          .socket
          .socket(posix.sys.socket.AF_INET6, posix.sys.socket.SOCK_STREAM | SOCK_NONBLOCK, 0)
        if (fd == -1)
          throw new RuntimeException(s"socket: ${errno.errno}")
        val ch = new EpollAsyncSocketChannel(fd)
        ch.ctlDel = epoll.ctl(
          fd,
          EpollExecutorScheduler.Read | EpollExecutorScheduler.Write | EpollExecutorScheduler.EdgeTriggered)(
          ch.callback(_))
        ch
      case _ => throw new RuntimeException("Global compute is not an EpollExecutorScheduler!")
    }
  }
}
