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

import epollcat.internal.net.EpollAsyncServerSocketChannel

import java.net.SocketAddress
import java.net.SocketOption
import java.nio.channels.spi.AsynchronousChannelProvider
import java.util.concurrent.Future

abstract class AsynchronousServerSocketChannel(val provider: AsynchronousChannelProvider)
    extends AsynchronousChannel {

  // used in fs2
  final def bind(local: SocketAddress): AsynchronousServerSocketChannel =
    bind(local, 0)

  def bind(local: SocketAddress, backlog: Int): AsynchronousServerSocketChannel

  // used in fs2
  def setOption[T](name: SocketOption[T], value: T): AsynchronousServerSocketChannel

  // used in fs2
  def accept[A](
      attachment: A,
      handler: CompletionHandler[AsynchronousSocketChannel, _ >: A]
  ): Unit

  def accept(): Future[AsynchronousSocketChannel]

  // used in fs2
  def getLocalAddress(): SocketAddress

}

object AsynchronousServerSocketChannel {
  def open(): AsynchronousServerSocketChannel =
    EpollAsyncServerSocketChannel.open()

  def open(group: AsynchronousChannelGroup): AsynchronousServerSocketChannel = {
    val _ = group
    open()
  }
}
