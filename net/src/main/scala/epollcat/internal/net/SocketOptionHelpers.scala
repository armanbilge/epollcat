package epollcat.internal.net

import scala.scalanative.posix
import scala.scalanative.unsafe._
import scala.scalanative.libc.errno
import java.io.IOException

private[net] object SocketOptionHelpers {

  def set(fd: CInt, option: CInt, value: Boolean): Unit = {
    val ptr = stackalloc[CInt]()
    !ptr = if (value.asInstanceOf[java.lang.Boolean]) 1 else 0
    if (posix
        .sys
        .socket
        .setsockopt(
          fd,
          posix.sys.socket.SOL_SOCKET,
          option,
          ptr.asInstanceOf[Ptr[Byte]],
          sizeof[CInt].toUInt) == -1)
      throw new IOException(s"setsockopt: ${errno.errno}")
  }

  def set(fd: CInt, option: CInt, value: Int): Unit = {
    val ptr = stackalloc[CInt]()
    !ptr = value
    if (posix
        .sys
        .socket
        .setsockopt(
          fd,
          posix.sys.socket.SOL_SOCKET,
          option,
          ptr.asInstanceOf[Ptr[Byte]],
          sizeof[CInt].toUInt) == -1)
      throw new IOException(s"setsockopt: ${errno.errno}")
  }

}
