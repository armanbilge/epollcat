addSbtPlugin("org.typelevel" % "sbt-typelevel" % "0.4.16")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.4.8")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.2.0")

libraryDependencySchemes ++= Seq(
  "org.scala-native" % "sbt-scala-native" % VersionScheme.Always
)
