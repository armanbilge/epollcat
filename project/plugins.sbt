resolvers ++= Resolver.sonatypeOssRepos("snapshots")
addSbtPlugin("org.typelevel" % "sbt-typelevel" % "0.4.13-15-6420590-SNAPSHOT")

addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.4.7")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.2.0")
