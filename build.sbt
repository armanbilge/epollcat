ThisBuild / tlBaseVersion := "0.0"

ThisBuild / organization := "com.armanbilge"
ThisBuild / organizationName := "Arman Bilge"
ThisBuild / developers += tlGitHubDev("armanbilge", "Arman Bilge")
ThisBuild / startYear := Some(2022)
ThisBuild / tlSonatypeUseLegacyHost := false

ThisBuild / crossScalaVersions := Seq("3.1.3", "2.12.16", "2.13.8")

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))

val catsEffectVersion = "3.4-f28b163-SNAPSHOT"
val munitCEVersion = "2.0-4e051ab-SNAPSHOT"

lazy val root = tlCrossRootProject.aggregate(core, tcp, tests, example)

lazy val core = project
  .in(file("core"))
  .enablePlugins(ScalaNativePlugin)
  .settings(
    name := "epollcat",
    libraryDependencies ++= Seq(
      "com.armanbilge" %%% "cats-effect" % catsEffectVersion,
      "com.armanbilge" %%% "munit-cats-effect" % munitCEVersion % Test
    )
  )

lazy val tcp = project
  .in(file("tcp"))
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(core)
  .settings(
    name := "epollcat-tcp"
  )

lazy val tests = crossProject(JVMPlatform, NativePlatform)
  .in(file("tests"))
  .enablePlugins(NoPublishPlugin)
  .nativeConfigure(_.dependsOn(tcp))
  .settings(
    libraryDependencies ++= Seq(
      "com.armanbilge" %%% "munit-cats-effect" % munitCEVersion % Test
    )
  )

lazy val example =
  project.in(file("example")).enablePlugins(ScalaNativePlugin, NoPublishPlugin).dependsOn(core)

ThisBuild / resolvers += "s01" at "https://s01.oss.sonatype.org/content/repositories/snapshots/"
