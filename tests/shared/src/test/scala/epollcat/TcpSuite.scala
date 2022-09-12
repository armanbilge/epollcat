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
import cats.syntax.all._

import java.net.BindException
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.charset.StandardCharsets
import scala.concurrent.duration._

class TcpSuite extends EpollcatSuite {

  override def munitIOTimeout = 20.seconds

  def decode(bb: ByteBuffer): String =
    StandardCharsets.UTF_8.decode(bb).toString()

/*
  test("connect localhost".only) {
    val address = new InetSocketAddress(InetAddress.getByName("localhost"), 0)
    IOSocketChannel.open.use { ch =>
      for {
        _ <- ch.connect(address)
      } yield ()
    }
  }
 */

/*
  test("connect localhost LeeT".only) {
  val localhost = new InetSocketAddress(InetAddress.getByName("localhost"), 8888)
  val `127.0.0.1` = new InetSocketAddress("127.0.0.1", 8888)

  IOServerSocketChannel
    .open
    .evalTap(_.bind(new InetSocketAddress("0.0.0.0", 8888)))
    .surround {
      for {
        result1 <- IOSocketChannel.open.use(_.connect(localhost)).attempt
        _ <- IO.println("\n")
        _ <- IO.println(result1)
        result2 <- IOSocketChannel.open.use(_.connect(`127.0.0.1`)).attempt
        _ <- IO.println("\n")
        _ <- IO.println(result2)
      } yield ()
    }
}
*/

  test("connect localhost LeeT".only) {
  val localhost = new InetSocketAddress(InetAddress.getByName("localhost"), 8888)
  val `127.0.0.1` = new InetSocketAddress("127.0.0.1", 8888)
  val lh = InetAddress.getByName("localhost")


  IOServerSocketChannel
    .open
    .evalTap(_.bind(new InetSocketAddress("0.0.0.0", 8888)))
    .surround {
      for {
        _ <- IO.println("\n")
        _ <- IO.println(
          s"InetAddress.getByName(localhost) is: |${lh}|\n")
        result1 <- IOSocketChannel.open.use(_.connect(localhost)).attempt
        _ <- IO.println("\n")
        _ <- IO.println(result1)
        result2 <- IOSocketChannel.open.use(_.connect(`127.0.0.1`)).attempt
        _ <- IO.println("\n")
        _ <- IO.println(result2)
      } yield ()
    }
}

  test("connect localhost") {
  val localhost = new InetSocketAddress(InetAddress.getByName("localhost"), 8888)
  val `127.0.0.1` = new InetSocketAddress("127.0.0.1", 8888)

  IOServerSocketChannel
    .open
    .evalTap(_.bind(new InetSocketAddress("0.0.0.0", 8888)))
    .surround {
      for {
        result1 <- IOSocketChannel.open.use(_.connect(localhost)).attempt
        _ <- IO.println(result1)
        result2 <- IOSocketChannel.open.use(_.connect(`127.0.0.1`)).attempt
        _ <- IO.println(result2)
      } yield ()
    }
}


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

        val client = for {
          serverAddr <- serverCh.localAddress
          _ <- clientCh.connect(serverAddr)
          clientLocalAddr <- clientCh.localAddress
          _ <- IO(
            assert(clue(clientLocalAddr.asInstanceOf[InetSocketAddress].getPort()) != 0)
          )
          bb <- IO(ByteBuffer.wrap("ping".getBytes))
          wrote <- clientCh.write(bb)
          _ <- IO(assertEquals(bb.remaining(), 0))
          _ <- IO(assertEquals(wrote, 4))
          bb <- IO(ByteBuffer.allocate(4))
          readed <- clientCh.read(bb)
          _ <- IO(assertEquals(readed, 4))
          _ <- IO(assertEquals(bb.remaining(), 0))
          res <- IO(bb.position(0)) *> IO(decode(bb))
          _ <- IO(assertEquals(res, "pong"))
        } yield ()

        serverCh.bind(new InetSocketAddress(0)) *> server.both(client).void
    }
  }

  test("local and remote addresses") {
    IOServerSocketChannel.open.evalTap(_.bind(
     new InetSocketAddress("localhost", 0))).use {
      server =>
        IOSocketChannel.open.use { clientCh =>
          server.localAddress.flatMap(clientCh.connect(_)) *>
            server.accept.use { serverCh =>
              for {
                serverLocal <- serverCh.localAddress
                serverRemote <- serverCh.remoteAddress
                clientLocal <- clientCh.localAddress
                clientRemote <- clientCh.remoteAddress
              } yield {
                assertEquals(clientRemote, serverLocal)
                assertEquals(serverRemote, clientLocal)
              }
            }
        }
    }
  }

  test("read after shutdownInput") {
    IOServerSocketChannel
      .open
      .evalTap(_.bind(new InetSocketAddress(0)))
      .evalMap(_.localAddress)
      .use { addr =>
        IOSocketChannel.open.use { ch =>
          for {
            _ <- ch.connect(addr)
            _ <- ch.shutdownInput
            readed <- ch.read(ByteBuffer.allocate(1))
            _ <- IO(assertEquals(readed, -1))
          } yield ()
        }
      }
  }

  test("options") {
    IOSocketChannel.open.use { ch =>
      ch.setOption(StandardSocketOptions.SO_REUSEADDR, java.lang.Boolean.TRUE) *>
        ch.setOption(StandardSocketOptions.SO_REUSEPORT, java.lang.Boolean.TRUE) *>
        ch.setOption(StandardSocketOptions.SO_SNDBUF, Integer.valueOf(1024)) *>
        ch.setOption(StandardSocketOptions.SO_RCVBUF, Integer.valueOf(1024)) *>
        ch.setOption(StandardSocketOptions.SO_KEEPALIVE, java.lang.Boolean.TRUE) *>
        ch.setOption(StandardSocketOptions.TCP_NODELAY, java.lang.Boolean.TRUE)
    }

    IOServerSocketChannel.open.use { ch =>
      ch.setOption(StandardSocketOptions.SO_REUSEADDR, java.lang.Boolean.TRUE) *>
        ch.setOption(StandardSocketOptions.SO_REUSEPORT, java.lang.Boolean.TRUE) *>
        ch.setOption(StandardSocketOptions.SO_RCVBUF, Integer.valueOf(1024))
    }
  }

  test("ConnectException") {
    IOServerSocketChannel
      .open
      .evalTap(_.bind(new InetSocketAddress(0)))
      .use(_.localAddress)
      .flatMap(addr => IOSocketChannel.open.use(_.connect(addr)))
      .interceptMessage[ConnectException]("Connection refused")
  }

  test("BindException - EADDRINUSE") {
    IOServerSocketChannel
      .open
      .evalTap(_.bind(new InetSocketAddress(0)))
      .evalMap(_.localAddress)
      .use(addr => IOServerSocketChannel.open.use(_.bind(addr)))
      .interceptMessage[BindException]("Address already in use")
  }

  test("BindException - EADDRNOTAVAIL") {
    // 240.0.0.1 is in reserved range 240.0.0.0/24.  Used to elicit Exception.
    IOServerSocketChannel
      .open
      .use { ch =>
        for {
          _ <- ch.bind(new InetSocketAddress("240.0.0.1", 0))
        } yield ()
      }
      .interceptMessage[BindException] {
        val osName = System.getProperty("os.name", "unknown").toLowerCase
        if (osName.startsWith("linux"))
          "Cannot assign requested address"
        else if (osName.startsWith("mac"))
          "Can't assign requested address"
        else
          "unknown operating system"
      }
  }

  test("ClosedChannelException") {
    IOServerSocketChannel
      .open
      .evalTap(_.bind(new InetSocketAddress(0)))
      .evalMap(_.localAddress)
      .use { addr =>
        IOSocketChannel.open.use { ch =>
          ch.connect(addr) *> ch.shutdownOutput *> ch.write(ByteBuffer.wrap(Array(1, 2, 3)))
        }
      }
      .intercept[ClosedChannelException]
  }

  test("server socket read does not block") {
    IOServerSocketChannel.open.use { server =>
      IOSocketChannel.open.use { clientCh =>
        for {
          _ <- server.bind(new InetSocketAddress(0))
          addr <- server.localAddress
          _ <- clientCh.connect(addr)
          _ <- clientCh.write(ByteBuffer.wrap("Hello!".getBytes))
          _ <- server.accept.use { serverCh =>
            for {
              bb <- IO(ByteBuffer.allocate(8192))
              _ <- serverCh.read(bb)
            } yield ()
          }
        } yield ()
      }
    }
  }

  test("IOServerSocketChannel.accept is cancelable") {
    // note that this test targets IOServerSocketChannel#accept,
    // not the underlying AsynchronousSocketChannel#accept implementation
    IOServerSocketChannel
      .open
      .evalTap(_.bind(new InetSocketAddress(0)))
      .flatMap(_.accept)
      .use_
      .timeoutTo(100.millis, IO.unit)
  }

}
