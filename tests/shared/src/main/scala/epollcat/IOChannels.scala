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

package epollcat

import cats.effect.IO
import cats.effect.kernel.Resource

import java.net.SocketAddress
import java.net.SocketOption
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler

import IOChannels._

final class IOSocketChannel(ch: AsynchronousSocketChannel) {
  def connect(remote: SocketAddress): IO[Unit] =
    IO.async_[Void](cb => ch.connect(remote, null, toHandler(cb))).void

  def read(dest: ByteBuffer): IO[Int] =
    IO.async_[Integer](cb => ch.read(dest, null, toHandler(cb))).map(_.intValue)

  def write(src: ByteBuffer): IO[Int] =
    IO.async_[Integer](cb => ch.write(src, null, toHandler(cb))).map(_.intValue)

  def shutdownInput: IO[Unit] = IO(ch.shutdownInput()).void

  def shutdownOutput: IO[Unit] = IO(ch.shutdownOutput()).void

  def setOption[T](option: SocketOption[T], value: T): IO[Unit] =
    IO(ch.setOption(option, value)).void

  def localAddress: IO[SocketAddress] =
    IO(ch.getLocalAddress())

  def remoteAddress: IO[SocketAddress] =
    IO(ch.getRemoteAddress())
}

object IOSocketChannel {
  def open: Resource[IO, IOSocketChannel] =
    Resource.fromAutoCloseable(IO(AsynchronousSocketChannel.open())).map(new IOSocketChannel(_))
}

final class IOServerSocketChannel(ch: AsynchronousServerSocketChannel) {
  def bind(local: SocketAddress): IO[Unit] =
    IO(ch.bind(local)).void

  def accept: Resource[IO, IOSocketChannel] =
    Resource
      .makeFull[IO, AsynchronousSocketChannel] { poll =>
        poll {
          IO.async { cb =>
            IO(ch.accept(null, toHandler(cb)))
              // it seems the only way to cancel accept is to close the socket :(
              .as(Some(IO(ch.close())))
          }
        }
      }(ch => IO(ch.close()))
      .map(new IOSocketChannel(_))

  def setOption[T](option: SocketOption[T], value: T): IO[Unit] =
    IO(ch.setOption(option, value)).void

  def localAddress: IO[SocketAddress] =
    IO(ch.getLocalAddress())
}

object IOServerSocketChannel {
  def open: Resource[IO, IOServerSocketChannel] =
    Resource
      .fromAutoCloseable(IO(AsynchronousServerSocketChannel.open()))
      .map(new IOServerSocketChannel(_))
}

object IOChannels {
  def toHandler[A](cb: Either[Throwable, A] => Unit): CompletionHandler[A, Any] =
    new CompletionHandler[A, Any] {
      def completed(result: A, attachment: Any): Unit = cb(Right(result))
      def failed(exc: Throwable, attachment: Any): Unit = cb(Left(exc))
    }
}
