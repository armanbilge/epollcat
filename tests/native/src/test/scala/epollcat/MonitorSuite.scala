package epollcat

import cats.effect.IO
import cats.effect.Resource
import epollcat.unsafe.EventPollingExecutorScheduler
import epollcat.unsafe.EpollRuntime

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.scalanative.posix.unistd._

class MonitorSuite extends EpollcatSuite {

  val zoneResource: Resource[IO, Zone] = Resource.make(IO(Zone.open()))(z => IO(z.close()))

  val pipeResource: Resource[IO, (Int, Int)] = Resource.make {
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
        println("CLOSING PIPES")
        close(a)
        close(b)
        ()
      }
  }

  test("compile") {
    val scheduler = EpollRuntime.global.scheduler.asInstanceOf[EventPollingExecutorScheduler]

    pipeResource.use {
      case (r, w) =>
        IO.fromFuture {
          IO {
            println("TEST STARTS")
            val promise = scala.concurrent.Promise[Unit]()
            val byte = 10.toByte
            scheduler.monitor(r, reads = true, writes = false)(
              new epollcat.unsafe.EventNotificationCallback {
                def notifyEvents(readReady: Boolean, writeReady: Boolean): Unit = {
                  println("NOTIFY EVENTS")
                  val buf = stackalloc[Byte]()
                  val bytesRead = read(r, buf, 1L.toULong)
                  assert(bytesRead == 1)
                  assert(buf(0) == byte)
                  println("SUCCESSFUL PROMISE")
                  promise.success(())
                }
              })
            val buf = stackalloc[Byte]()
            buf(0) = byte
            val bytesWrote = write(w, buf, 1L.toULong)
            assert(bytesWrote == 1)
            println("RETURNING FUTURE")
            promise.future
          }
        }
    }
  }
}
