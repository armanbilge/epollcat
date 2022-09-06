package epollcat.internal.ch

import scala.annotation.nowarn
import scala.scalanative.unsafe._

@extern
@nowarn
private[ch] object socket {
  final val SOCK_NONBLOCK = 2048

  def accept(sockfd: CInt, addr: Ptr[Byte], addrlen: Ptr[Byte]): CInt = extern

  def accept4(sockfd: CInt, addr: Ptr[Byte], addrlen: Ptr[Byte], flags: CInt): CInt = extern
}
