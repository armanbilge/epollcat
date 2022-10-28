package epollcat

import cats.effect.IO
import cats.effect.Resource
import epollcat.unsafe.EventNotificationCallback
import epollcat.unsafe.EventPollingExecutorScheduler
import epollcat.unsafe.EpollRuntime

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.scalanative.posix.unistd._

class MonitorSuite extends EpollcatSuite {

  private val zoneResource: Resource[IO, Zone] =
    Resource.make(IO(Zone.open()))(z => IO(z.close()))

  private val pipeResource: Resource[IO, (Int, Int)] = Resource.make {
    zoneResource.use { implicit zone =>
      val fildes = alloc[CInt](2)
      if (pipe(fildes) != 0) {
        IO.raiseError(new Exception("Failed to create pipe"))
      } else {
        IO((fildes(0), fildes(1)))
      }
    }
  } {
    case (a, b) =>
      IO {
        close(a)
        close(b)
        ()
      }
  }

  test("monitor a pipe") {
    val scheduler = EpollRuntime.global.scheduler.asInstanceOf[EventPollingExecutorScheduler]

    pipeResource.use {
      case (r, w) =>
        IO.async_[Unit] { cb =>
          val byte = 10.toByte
          var stop: Runnable = null
          val monitorCallback = new epollcat.unsafe.EventNotificationCallback {
            def notifyEvents(readReady: Boolean, writeReady: Boolean): Unit = {
              val readBuf = stackalloc[Byte]()
              val bytesRead = read(r, readBuf, 1L.toULong)
              assertEquals(bytesRead, 1)
              assertEquals(readBuf(0), byte)
              stop.run()
              cb(Right(()))
            }
          }
          stop = scheduler.monitor(r, reads = true, writes = false)(monitorCallback)
          val writeBuf = stackalloc[Byte]()
          writeBuf(0) = byte
          val wroteBytes = write(w, writeBuf, 1L.toULong)
          assertEquals(wroteBytes, 1)
        }
    }
  }
}
