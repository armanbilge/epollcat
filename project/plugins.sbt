resolvers ++= Resolver.sonatypeOssRepos("snapshots")
addSbtPlugin("org.typelevel" % "sbt-typelevel" % "0.4.13-13-f722d9e-SNAPSHOT")

addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.4.7")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.2.0")
