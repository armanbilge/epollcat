ThisBuild / tlBaseVersion := "0.0"

ThisBuild / organization := "com.armanbilge"
ThisBuild / organizationName := "Arman Bilge"
ThisBuild / developers += tlGitHubDev("armanbilge", "Arman Bilge")
ThisBuild / startYear := Some(2022)
ThisBuild / tlSonatypeUseLegacyHost := false

ThisBuild / crossScalaVersions := Seq("3.1.3", "2.12.16", "2.13.8")

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))

lazy val root = tlCrossRootProject.aggregate(core)

lazy val core = project
  .in(file("core"))
  .enablePlugins(ScalaNativePlugin)
  .settings(
    name := "epollcat",
    libraryDependencies ++= Seq(
      "com.armanbilge" %%% "cats-effect" % "3.4-dfca087-SNAPSHOT",
      "com.armanbilge" %%% "munit-cats-effect" % "2.0-4e051ab-SNAPSHOT" % Test
    )
  )

ThisBuild / resolvers += "s01" at "https://s01.oss.sonatype.org/content/repositories/snapshots/"
