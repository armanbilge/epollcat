ThisBuild / tlBaseVersion := "0.1"

ThisBuild / organization := "com.armanbilge"
ThisBuild / organizationName := "Arman Bilge"
ThisBuild / developers += tlGitHubDev("armanbilge", "Arman Bilge")
ThisBuild / startYear := Some(2022)

ThisBuild / crossScalaVersions := Seq("3.3.4", "2.12.20", "2.13.16")
ThisBuild / tlJdkRelease := None

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
ThisBuild / githubWorkflowOSes := Seq(
  "ubuntu-20.04",
  "ubuntu-22.04",
  "ubuntu-22.04-arm",
  "ubuntu-24.04",
  "macos-13",
  "macos-14"
)
ThisBuild / githubWorkflowBuildMatrixExclusions ++= {
  for {
    scala <- List("3", "2.12")
    os <- githubWorkflowOSes.value.toSet -- Set("ubuntu-24.04", "macos-14")
  } yield MatrixExclude(Map("scala" -> scala, "os" -> os))
}

import com.github.sbt.git.SbtGit.GitKeys._
ThisBuild / useConsoleForROGit := true

ThisBuild / githubWorkflowBuild ++= Seq(
  WorkflowStep.Sbt(
    List("example/run"),
    name = Some("Run the example"),
    cond = Some("matrix.project == 'rootNative'")
  )
)

val catsEffectVersion = "3.6.0"
val munitCEVersion = "2.0.0"

lazy val root = tlCrossRootProject.aggregate(core, tests, example)

lazy val core = project
  .in(file("core"))
  .enablePlugins(ScalaNativePlugin)
  .settings(
    name := "epollcat",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % catsEffectVersion
    )
  )

lazy val tests = crossProject(JVMPlatform, NativePlatform)
  .in(file("tests"))
  .enablePlugins(NoPublishPlugin)
  .nativeConfigure(_.dependsOn(core))
  .settings(
    // https://github.com/scalameta/munit/blob/92710a507339d20368d251feadf66e4f9f4e1840/junit-interface/src/main/java/munit/internal/junitinterface/JUnitRunner.java#L75
    // note that the equivalent `--logger=sbt` did not work for some reason
    // using the sbt logger prevents tests from different suites being interleaved
    Test / testOptions += Tests.Argument("+l"),
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % catsEffectVersion,
      "org.typelevel" %%% "munit-cats-effect" % munitCEVersion % Test
    )
  )
  .jvmSettings(fork := true)

lazy val example = project
  .in(file("example"))
  .enablePlugins(ScalaNativePlugin, NoPublishPlugin)
  .dependsOn(tests.native)
