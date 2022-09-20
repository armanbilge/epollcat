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

import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.net.BindException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.charset.StandardCharsets

import scala.concurrent.duration._

/* To manually monitor server connections on Linux: $netstat -tlpe -c
 *  -l is listen (server) -c is continuous
 */

object NetworkProtocolInfo {

  def epollcatUsesIPv4(): Boolean = {
    val ch = AsynchronousServerSocketChannel.open

    val haveIPv6 =
      try {
        ch.bind(new InetSocketAddress("::0", 0))

        val nAddrBytes =
          ch.getLocalAddress.asInstanceOf[InetSocketAddress].getAddress().getAddress().size

        nAddrBytes == 16
      } catch {
        /*  The exact Exception thrown can and does differ by
         *  operating system ^& IP protocol. They are all Exceptions.
         *    EAI_ADDRFAMILY, is thrown on linux, freeBSD. It is not POSIX,
         *       and the constant is different on each. Catch the
         *       IOException they have in common.
         *    BindException is defined, and tends to happen on macOS
         */
        case e: BindException => throw e // Apple and ??
        case _: java.io.IOException => false // expected on Linux IPv4
        // _Everything else is unexpected so let it bubble up.
      } finally {
        ch.close()
      }

    !haveIPv6
  }

}

/*  Futures:
 *    The ordering of output seems to be indeterminant.
 *    That is, the output from a given test does not always
 *    appear directly below/after its Suite.  It can look
 *    like the Test belongs in another Suite.
 *    Naming tests can give a clue that the real work is being done
 *    and by whom, but the interleaving is, IMO, a defect.
 */

class Tcp6NotPresentSuite extends EpollcatSuite {
  override def munitIOTimeout = 10.seconds

  override def munitIgnore: Boolean =
    NetworkProtocolInfo.epollcatUsesIPv4() == false

  test("Bind ::1 - No IPv6") {

    /* Linux, macOS, and possibly others use different message text.
     * Test for an Exception occuring, but not for exact text.
     * Avoiding Scala Native LinktimeInfo allows same test to run on both
     * Scala Native and JVM. Very helpful.
     */

    /* Testing for an Exception is ugly, brutal, and effective.
     * It is hard to peel away enough layers of abstraction to determine
     * the underlying IP address_family/protocol.
     */

    IOServerSocketChannel
      .open
      .evalTap(_.setOption(StandardSocketOptions.SO_REUSEADDR, java.lang.Boolean.TRUE))
      .use { ch =>
        for {
          _ <- ch.bind(new InetSocketAddress("::1", 0))
        } yield ()
      }
      .intercept[java.io.IOException]
  }
}

class Tcp6Suite extends EpollcatSuite {

  /* 2022-09-12
   *      The IPv6 wildcard address must be explicitly given to
   *      InetSocketAddress on SN 0.4.n, fixed in 0.5.0-SNAPSHOT.
   *         NO: serverCh.bind(new InetSocketAddress(0))
   *         Yes: serverCh.bind(new InetSocketAddress("::", 0))
   *       Tcp6Suite found a good one!
   */

  override def munitIOTimeout = 20.seconds

  override def munitIgnore: Boolean = NetworkProtocolInfo.epollcatUsesIPv4()

  def decode(bb: ByteBuffer): String =
    StandardCharsets.UTF_8.decode(bb).toString()

  // Checking IPv6 wildcard usage in default situations needs extra scrutiny.
  test("Bind :: - IPv6 wildcard") {
    IOServerSocketChannel
      .open
      .evalTap(_.setOption(StandardSocketOptions.SO_REUSEADDR, java.lang.Boolean.TRUE))
      .use { ch =>
        for {
          // On Scala Native 0.4.n InetSocketAddress(0) will use IPv4, WRONG!
          isa <- IO(new InetSocketAddress("::", 0))
          _ <- IO(assert(clue(isa.getAddress.isInstanceOf[Inet6Address])))
          _ <- ch.bind(isa)
        } yield ()
      }
  }

  test("Bind ::1") {
    IOServerSocketChannel
      .open
      .evalTap(_.setOption(StandardSocketOptions.SO_REUSEADDR, java.lang.Boolean.TRUE))
      .use { ch =>
        for {
          _ <- ch.bind(new InetSocketAddress("::1", 0))
        } yield ()
      }
  }

  test("Bind ip6-localhost") {
    IOServerSocketChannel
      .open
      .evalTap(_.setOption(StandardSocketOptions.SO_REUSEADDR, java.lang.Boolean.TRUE))
      .use { ch =>
        for {
          _ <- ch.bind(new InetSocketAddress("ip6-localhost", 0))
        } yield ()
      }
  }

  // Manually verified with netstat that IPv6 wildcard is being used on wire.
  test("server-client ping-pong ::0 IPv6") {
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

        serverCh.bind(new InetSocketAddress("::", 0)) *> server.both(client).void
    }
  }

  test("local and remote addresses ::1") {
    IOServerSocketChannel
      .open
      .evalTap(_.bind(new InetSocketAddress("::1", 0)))
      .evalTap(_.setOption(StandardSocketOptions.SO_REUSEADDR, java.lang.Boolean.TRUE))
      .use { server =>
        IOSocketChannel.open.use { clientCh =>
          server.localAddress.flatMap(clientCh.connect(_)) *>
            server.accept.use { serverCh =>
              for {
                _ <- serverCh.shutdownOutput
                _ <- clientCh.shutdownOutput
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

  test("local and remote addresses ip6-localhost") {
    IOServerSocketChannel.open.evalTap(_.bind(new InetSocketAddress("ip6-localhost", 0))).use {
      server =>
        IOSocketChannel.open.use { clientCh =>
          server.localAddress.flatMap(clientCh.connect(_)) *>
            server.accept.use { serverCh =>
              for {
                _ <- serverCh.shutdownOutput
                _ <- clientCh.shutdownOutput
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

  /* Operating systems differ as to whether binding to ::0 causes the server
   * to listen on both ::0 and 127.0.0.1 or just the former. Linux listens
   * on both. The BSDs tend to listen on just the former.
   */

  /*
   * This test shows that listening on ::0 will indeed pick up a connection
   * to another interface on the machine, not just the addressed passed as a
   * variable in previous tests.
   */
  test("server-client ping-pong server ::0, client ::1") {
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
          serverLocalAddr <- serverCh.localAddress
          port <- IO(serverLocalAddr.asInstanceOf[InetSocketAddress].getPort)
          dstAddr <- IO(new InetSocketAddress("::1", port))
          _ <- clientCh.connect(dstAddr)
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

        serverCh.bind(new InetSocketAddress("::", 0)) *> server.both(client).void
    }
  }

}
