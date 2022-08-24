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

import java.io.IOException
import java.net.SocketAddress
import java.net.SocketOption
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import scala.scalanative.annotation.stub
import scala.scalanative.libc.errno
import scala.scalanative.posix

final class EpollAsyncSocketChannel(fd: Int) extends AsynchronousSocketChannel(null) {

  private[this] var _isOpen: Boolean = true

  def close(): Unit = {
    _isOpen = false
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
  ): Unit = ???

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

  def setOption[T](name: SocketOption[T], value: T): AsynchronousSocketChannel = ???

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
  ): Unit = ???

  def getLocalAddress(): SocketAddress = ???

  @stub
  def supportedOptions(): java.util.Set[SocketOption[_]] = ???

}
