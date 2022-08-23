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

import java.net.SocketAddress
import java.net.SocketOption
import java.nio.ByteBuffer

abstract class AsynchronousSocketChannel extends Channel {

  def connect[A](
      remote: SocketAddress,
      attachment: A,
      handler: CompletionHandler[Void, A]
  ): Unit

  def read[A](src: ByteBuffer, attachent: A, handler: CompletionHandler[Integer, _ >: A]): Unit

  def write[A](src: ByteBuffer, attachent: A, handler: CompletionHandler[Integer, _ >: A]): Unit

  def shutdownInput(): AsynchronousSocketChannel

  def shutdownOutput(): AsynchronousSocketChannel

  def getLocalAddress(): SocketAddress

  def getRemoteAddress(): SocketAddress

  def setOption[T](name: SocketOption[T], value: T): AsynchronousSocketChannel

}
