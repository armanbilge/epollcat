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
import cats.effect.Resource
import epollcat.unsafe.EventNotificationCallback
import epollcat.unsafe.EventPollingExecutorScheduler
import epollcat.unsafe.EpollRuntime

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.scalanative.posix.fcntl._
import scala.scalanative.posix.unistd._

class MonitorSuite extends EpollcatSuite {

  class Pipe private (val readFd: Int, val writeFd: Int)
  object Pipe {
    val make: Resource[IO, Pipe] =
      Resource
        .make {
          IO {
            val fildes = stackalloc[CInt](2.toUInt)
            if (pipe(fildes) != 0) {
              throw new Exception("Failed to create pipe")
            } else {
              new Pipe(fildes(0), fildes(1))
            }
          }
        }(pipe =>
          IO {
            close(pipe.readFd)
            close(pipe.writeFd)
            ()
          })
        .evalTap { pipe =>
          IO {
            if (fcntl(pipe.readFd, F_SETFL, O_NONBLOCK) != 0)
              throw new Exception("fcntl")
            if (fcntl(pipe.writeFd, F_SETFL, O_NONBLOCK) != 0)
              throw new Exception("fcntl")
          }
        }
  }

  test("monitor a pipe") {
    val scheduler = EpollRuntime.global.scheduler.asInstanceOf[EventPollingExecutorScheduler]

    Pipe.make.use { pipe =>
      IO.async_[Unit] { cb =>
        val byte = 10.toByte
        var stop: Runnable = null
        val monitorCallback = new EventNotificationCallback {
          def notifyEvents(readReady: Boolean, writeReady: Boolean): Unit = {
            try {
              val readBuf = stackalloc[Byte]()
              val bytesRead = read(pipe.readFd, readBuf, 1L.toULong)
              assertEquals(bytesRead, 1)
              assertEquals(readBuf(0), byte)
              cb(Right(()))
            } catch {
              case e: Throwable => cb(Left(e))
            } finally {
              stop.run()
            }
          }
        }
        stop = scheduler.monitor(pipe.readFd, reads = true, writes = false)(monitorCallback)
        val writeBuf = stackalloc[Byte]()
        writeBuf(0) = byte
        val wroteBytes = write(pipe.writeFd, writeBuf, 1L.toULong)
        assertEquals(wroteBytes, 1)
      }
    }
  }
}
