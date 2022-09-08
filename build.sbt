ThisBuild / tlBaseVersion := "0.0"

ThisBuild / organization := "com.armanbilge"
ThisBuild / organizationName := "Arman Bilge"
ThisBuild / developers += tlGitHubDev("armanbilge", "Arman Bilge")
ThisBuild / startYear := Some(2022)
ThisBuild / tlSonatypeUseLegacyHost := false

ThisBuild / crossScalaVersions := Seq("3.1.3", "2.12.16", "2.13.8")

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))

val catsEffectVersion = "3.4-519e5ce-SNAPSHOT"
val munitCEVersion = "2.0-17d21ae-SNAPSHOT"

lazy val root = tlCrossRootProject.aggregate(core, tests)

lazy val core = project
  .in(file("core"))
  .enablePlugins(ScalaNativePlugin)
  .settings(
    name := "epollcat",
    libraryDependencies ++= Seq(
      "com.armanbilge" %%% "cats-effect" % catsEffectVersion
    )
  )

lazy val tests = crossProject(JVMPlatform, NativePlatform)
  .in(file("tests"))
  .enablePlugins(NoPublishPlugin)
  .nativeConfigure(_.dependsOn(core))
  .nativeSettings(
    libraryDependencies ++= Seq(
      "com.armanbilge" %%% "munit-cats-effect" % munitCEVersion % Test
    )
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "munit-cats-effect" % "2.0.0-M2" % Test
    )
  )

ThisBuild / resolvers += "s01" at "https://s01.oss.sonatype.org/content/repositories/snapshots/"
