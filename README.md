# epollcat

An experimental [I/O-integrated runtime](https://github.com/typelevel/cats-effect/discussions/3070) for [Cats Effect](https://typelevel.org/cats-effect/) on [Scala Native](https://scala-native.org/), implemented with the [`epoll` API](https://man7.org/linux/man-pages/man7/epoll.7.html) on Linux and the [`kqueue` API](https://en.wikipedia.org/wiki/Kqueue) on macOS.

The primary goal of this project is to provide implementations for Java I/O APIs used in the [fs2-io](https://fs2.io/#/io) library so that it can cross-build for Scala Native. This in turn enables projects such as [http4s Ember](https://http4s.org/) and [Skunk](https://tpolecat.github.io/skunk/) to cross-build for Native as well.

Please try it and contribute bug reports and fixes! Snapshots are available [here](https://s01.oss.sonatype.org/content/repositories/snapshots/com/armanbilge/epollcat_native0.4_3/) for Scala 2.12, 2.13, and 3.

```scala
resolvers ++= Resolver.sonatypeOssRepos("snapshots")
libraryDependencies ++= Seq(
  "com.armanbilge" %%% "epollcat" % "0.0-dae8f73-SNAPSHOT" // or latest commit
)
```

## FAQ

### How can I use this?

There are a couple options:

1. Where you would normally use `IOApp` or `IOApp.Simple`, simply use `EpollApp` or `EpollApp.Simple` instead: it's a drop-in replacement.

2. For test frameworks and other unusual situations, you may need to use `EpollRuntime.global`. For example to use with [munit-cats-effect](https://github.com/typelevel/munit-cats-effect):
```scala
 override def munitIORuntime = EpollRuntime.global
```

### Should I add this as a dependency to my Scala Native library?

I do not recommend it. This project is intended for only applications, it is too opinionated for libraries to force it onto their users:
1. It supports only Linux and macOS.
2. It partially implements `java.*` APIs which it does not hold the "namespace rights" to, but these implementations are not canonical nor do they intend to be.
3. Your user may want to use a different runtime in their application (for example the `CurlRuntime` from [http4s-curl](https://github.com/http4s/http4s-curl/)). It is not possible to mix runtimes; expect bad things to happen.

However, you may want to add this dependency in your project's _Test_ scope so that you can run your test suite on Native. You may also recommend Native users of your library add this dependency to their applications.

### Do I have have to use Cats Effect and FS2?

Actually, no :) inside `EpollRuntime.global` you will find a vanilla `ExecutionContext` and `Scheduler` with APIs in terms of Java `Runnable`. You can use these to run your Scala `Future`s or other effect types and interact directly with the Java APIs.

### macOS support?

Despite the project name, epollcat supports macOS as well via the [`kqueue` API](https://en.wikipedia.org/wiki/Kqueue).

### Windows support?

Sorry, nope :)

### Why not use libuv?

First of all, I think a [libuv](https://libuv.org/)-based runtime is a great idea, particularly due to the cross-platform compatibility and also because it provides async DNS and file system APIs (via its own blocking pool). If anyone wants to work on this I would be very happy to help you get started!

I thought a lot about this and where I should best put my time and energy. Here is some of my reasoning.

1. Actually, libuv is a bit too high-level for our needs: it essentially offers an entire runtime, including a scheduler and a blocking pool. Ideally we would use only its cross-platform I/O polling capability within our own Cats Effect runtime, but this does not seem to be exposed unfortunately.

    This becomes especially relevant when Scala Native supports multi-threading. Cats Effect JVM ships with a [fantastic runtime](https://typelevel.org/cats-effect/docs/schedulers#jvm) that I am hopeful we can eventually cross-build for Native. Meanwhile, the libuv event loop is fundamentally single-threaded.

    Since in the long run implementing our own async I/O library seems inevitable, this project makes an early investment in that direction.

2. Dependencies on native libraries create build complexity, and build complexity is a [burden on maintainers](https://github.com/typelevel/scalacheck-xml/pull/1#issuecomment-1158140151) as well as contributors.

3. epollcat is a pretty cool name and I couldn't think of anything comparable for libuv :) besides unicorn velociraptors probably eat cats for lunch! 
