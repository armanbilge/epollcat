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

package java.nio.channels

import epollcat.internal.ch.EpollAsyncSocketChannel

import java.net.SocketAddress
import java.net.SocketOption
import java.nio.ByteBuffer
import java.nio.channels.spi.AsynchronousChannelProvider
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

abstract class AsynchronousSocketChannel(val provider: AsynchronousChannelProvider)
    extends AsynchronousChannel {

  def bind(local: SocketAddress): AsynchronousSocketChannel

  // used in fs2
  def setOption[T](name: SocketOption[T], value: T): AsynchronousSocketChannel

  // used in fs2
  def shutdownInput(): AsynchronousSocketChannel

  // used in fs2
  def shutdownOutput(): AsynchronousSocketChannel

  // used in fs2
  def getRemoteAddress(): SocketAddress

  // used in fs2
  def connect[A](
      remote: SocketAddress,
      attachment: A,
      handler: CompletionHandler[Void, _ >: A]
  ): Unit

  def connect(
      remote: SocketAddress
  ): Future[Void]

  def read[A](
      dst: ByteBuffer,
      timeout: Long,
      unit: TimeUnit,
      attachment: A,
      handler: CompletionHandler[Integer, _ >: A]
  ): Unit

  // used in fs2
  final def read[A](
      dst: ByteBuffer,
      attachment: A,
      handler: CompletionHandler[Integer, _ >: A]
  ): Unit =
    read(dst, 0, TimeUnit.MILLISECONDS, attachment, handler)

  def read(dst: ByteBuffer): Future[Integer]

  def read[A](
      dsts: Array[ByteBuffer],
      offset: Int,
      length: Int,
      timeout: Long,
      unit: TimeUnit,
      attachment: A,
      handler: CompletionHandler[java.lang.Long, _ >: A]
  ): Unit

  def write[A](
      src: ByteBuffer,
      timeout: Long,
      unit: TimeUnit,
      attachment: A,
      handler: CompletionHandler[Integer, _ >: A]
  ): Unit

  // used in fs2
  final def write[A](
      src: ByteBuffer,
      attachment: A,
      handler: CompletionHandler[Integer, _ >: A]
  ): Unit =
    write(src, 0, TimeUnit.MILLISECONDS, attachment, handler)

  def write(src: ByteBuffer): Future[Integer]

  def write[A](
      srcs: Array[ByteBuffer],
      offset: Int,
      length: Int,
      timeout: Long,
      unit: TimeUnit,
      attachment: A,
      handler: CompletionHandler[java.lang.Long, _ >: A]
  ): Unit

  // used in fs2
  def getLocalAddress(): SocketAddress

}

object AsynchronousSocketChannel {
  def open(): AsynchronousSocketChannel =
    EpollAsyncSocketChannel.open()

  def open(group: AsynchronousChannelGroup): AsynchronousSocketChannel = {
    val _ = group
    open()
  }
}
