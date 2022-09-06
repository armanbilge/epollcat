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

package epollcat.unsafe

import scala.annotation.nowarn
import scala.scalanative.posix.time._
import scala.scalanative.unsafe._

@extern
@nowarn
private[unsafe] object event {

  final val EVFILT_READ = -1
  final val EVFILT_WRITE = -2

  final val KEVENT_FLAG_NONE = 0x000000
  final val KEVENT_FLAG_IMMEDIATE = 0x000001

  final val EV_ADD = 0x0001
  final val EV_DELETE = 0x0002
  final val EV_CLEAR = 0x0020

  type kevent64_s

  def kqueue(): CInt = extern

  def kevent64(
      kq: CInt,
      changelist: Ptr[kevent64_s],
      nchanges: CInt,
      eventlist: Ptr[kevent64_s],
      nevents: CInt,
      flags: CUnsignedInt,
      timeout: Ptr[timespec]
  ): CInt = extern

}

private[unsafe] object eventImplicits {

  import event._

  implicit final class kevent64_sOps(kevent64_s: Ptr[kevent64_s]) {
    def ident: CUnsignedLongInt = !(kevent64_s.asInstanceOf[Ptr[CUnsignedLongInt]])
    def ident_=(ident: CUnsignedLongInt): Unit =
      !(kevent64_s.asInstanceOf[Ptr[CUnsignedLongInt]]) = ident

    def filter: CShort = !(kevent64_s.asInstanceOf[Ptr[CShort]] + 4)
    def filter_=(filter: CShort): Unit =
      !(kevent64_s.asInstanceOf[Ptr[CShort]] + 4) = filter

    def flags: CUnsignedShort = !(kevent64_s.asInstanceOf[Ptr[CUnsignedShort]] + 5)
    def flags_=(flags: CUnsignedShort): Unit =
      !(kevent64_s.asInstanceOf[Ptr[CUnsignedShort]] + 5) = flags

    def udata: Ptr[Byte] = !(kevent64_s.asInstanceOf[Ptr[Ptr[Byte]]] + 4)
    def udata_=(udata: Ptr[Byte]): Unit =
      !(kevent64_s.asInstanceOf[Ptr[Ptr[Byte]]] + 4) = udata
  }

  implicit val kevent64_sTag: Tag[kevent64_s] =
    Tag.materializeCArrayTag[Byte, Nat.Digit2[Nat._4, Nat._8]].asInstanceOf[Tag[kevent64_s]]

}
