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
package tcp

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all._

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.SocketOption
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.nio.charset.StandardCharsets
import java.net.StandardSocketOptions

class TcpSuite extends EpollcatSuite {

  def toHandler[A](cb: Either[Throwable, A] => Unit): CompletionHandler[A, Any] =
    new CompletionHandler[A, Any] {
      def completed(result: A, attachment: Any): Unit = cb(Right(result))
      def failed(exc: Throwable, attachment: Any): Unit = cb(Left(exc))
    }

  final class IOSocketChannel(ch: AsynchronousSocketChannel) {
    def connect(remote: SocketAddress): IO[Unit] =
      IO.async_[Void](cb => ch.connect(remote, null, toHandler(cb))).void

    def read(dest: ByteBuffer): IO[Int] =
      IO.async_[Integer](cb => ch.read(dest, null, toHandler(cb))).map(_.intValue)

    def write(src: ByteBuffer): IO[Int] =
      IO.async_[Integer](cb => ch.write(src, null, toHandler(cb))).map(_.intValue)

    def setOption[T](option: SocketOption[T], value: T): IO[Unit] =
      IO(ch.setOption(option, value)).void
  }

  object IOSocketChannel {
    def open: Resource[IO, IOSocketChannel] =
      Resource
        .fromAutoCloseable(IO(AsynchronousSocketChannel.open()))
        .map(new IOSocketChannel(_))
  }

  final class IOServerSocketChannel(ch: AsynchronousServerSocketChannel) {
    def bind(local: SocketAddress): IO[Unit] =
      IO(ch.bind(local)).void

    def accept: Resource[IO, IOSocketChannel] =
      Resource
        .make(IO.async_[AsynchronousSocketChannel](cb => ch.accept(null, toHandler(cb))))(ch =>
          IO(ch.close()))
        .map(new IOSocketChannel(_))

    def setOption[T](option: SocketOption[T], value: T): IO[Unit] =
      IO(ch.setOption(option, value)).void
  }

  object IOServerSocketChannel {
    def open: Resource[IO, IOServerSocketChannel] =
      Resource
        .fromAutoCloseable(IO(AsynchronousServerSocketChannel.open()))
        .map(new IOServerSocketChannel(_))
  }

  def decode(bb: ByteBuffer): String =
    StandardCharsets.UTF_8.decode(bb).toString()

  test("HTTP echo") {
    val address = new InetSocketAddress(InetAddress.getByName("postman-echo.com"), 80)
    val bytes =
      """|GET /get HTTP/1.1
         |Host: postman-echo.com
         |
         |""".stripMargin.getBytes()

    IOSocketChannel.open.use { ch =>
      for {
        _ <- ch.connect(address)
        wrote <- ch.write(ByteBuffer.wrap(bytes))
        _ <- IO(assertEquals(wrote, bytes.length))
        bb <- IO(ByteBuffer.allocate(1024))
        readed <- ch.read(bb)
        _ <- IO(assert(clue(readed) > 0))
        res <- IO(bb.position(0)) *> IO(decode(bb))
        _ <- IO(assert(clue(res).startsWith("HTTP/1.1 200 OK")))
      } yield ()
    }
  }

  test("server-client ping-pong") {
    (
      IOServerSocketChannel
        .open
        .evalTap(_.setOption(StandardSocketOptions.SO_REUSEADDR, java.lang.Boolean.TRUE)),
      IOSocketChannel.open
    ).tupled.use {
      case (serverCh, clientCh) =>
        val addr = new InetSocketAddress(4242)

        val server = serverCh.accept.use { ch =>
          for {
            bb <- IO(ByteBuffer.allocate(4))
            readed <- ch.read(bb)
            _ <- IO(assertEquals(readed, 4))
            res <- IO(bb.position(0)) *> IO(decode(bb))
            _ <- IO(assertEquals(res, "ping"))
            wrote <- ch.write(ByteBuffer.wrap("pong".getBytes))
            _ <- IO(assertEquals(wrote, 4))
          } yield ()
        }

        serverCh.bind(addr) *> server.background.use { _ =>
          val ch = clientCh
          for {
            _ <- ch.connect(addr)
            wrote <- ch.write(ByteBuffer.wrap("ping".getBytes))
            _ <- IO(assertEquals(wrote, 4))
            bb <- IO(ByteBuffer.allocate(4))
            readed <- ch.read(bb)
            _ <- IO(assertEquals(readed, 4))
            res <- IO(bb.position(0)) *> IO(decode(bb))
            _ <- IO(assertEquals(res, "pong"))
          } yield ()
        }
    }
  }

  test("options") {
    IOSocketChannel.open.use { ch =>
      ch.setOption(StandardSocketOptions.SO_REUSEADDR, java.lang.Boolean.TRUE) *>
        ch.setOption(StandardSocketOptions.SO_REUSEPORT, java.lang.Boolean.TRUE) *>
        ch.setOption(StandardSocketOptions.SO_SNDBUF, Integer.valueOf(1024)) *>
        ch.setOption(StandardSocketOptions.SO_RCVBUF, Integer.valueOf(1024))
    }

    IOServerSocketChannel.open.use { ch =>
      ch.setOption(StandardSocketOptions.SO_REUSEADDR, java.lang.Boolean.TRUE) *>
        ch.setOption(StandardSocketOptions.SO_REUSEPORT, java.lang.Boolean.TRUE) *>
        ch.setOption(StandardSocketOptions.SO_RCVBUF, Integer.valueOf(1024))
    }
  }

}
