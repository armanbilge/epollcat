# epollcat

A minimum viable runtime demoing asynchronous I/O in Cats Effect Native, based on the [`epoll` API](https://man7.org/linux/man-pages/man7/epoll.7.html).

Try out the [example app](example/src/main/scala/example/TpolChiclet.scala), which demonstrates a non-blocking `Console[IO].readLine`.
```
sbt example/run
```
